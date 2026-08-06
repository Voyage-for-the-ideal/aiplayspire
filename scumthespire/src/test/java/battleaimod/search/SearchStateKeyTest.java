package battleaimod.search;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import savestate.StateJsonHelper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SearchStateKeyTest {
    @Test
    public void preservesCurrentSha256Digests() {
        assertEquals("3874dfc4af065b561d52f1030da74edf1f11cabf9efb0f1cee932946aeb5493e",
                key("{\"turn\":2,\"rng\":\"10\",\"cards\":[\"A\",\"B\"]}").toString());
        assertEquals("91a1ebe182e65c655ba7a5cc4a251a08b2fd61d14737894320335ccc64c24612",
                key("{\"z\":null,\"unicode\":\"\\u96ea\\n\\\"\",\"number\":1.25,\"bool\":true,"
                        + "\"array\":[3,\"x\"],\"card\":{\"uuid\":\"client-uuid\",\"id\":\"A\"}}")
                        .toString());
        assertEquals("5a89a8b8a0fa4a8ac2e06aa48b86a7689a04db8d0f95beb292a0a0cd29f6f00e",
                key(cardHistory("client-uuid", "client-uuid")).toString());
    }

    @Test
    public void rendersLowercaseSha256Hex() {
        String encoded = key("{\"turn\":1}").toString();
        SearchStateKey key = key("{\"turn\":1}");
        String firstEncoding = key.toString();

        assertEquals("sha256", SearchStateKey.algorithm());
        assertEquals(64, encoded.length());
        assertTrue(encoded.matches("[0-9a-f]{64}"));
        assertSame(firstEncoding, key.toString());
    }

    @Test
    public void matchesGsonEncodingForEscapedAndUnicodeStrings() {
        String value = "<>&='\\\"\t\b\n\r\f" + (char) 0x01 + (char) 0x2028 + (char) 0x2029
                + new String(Character.toChars(0x1f603)) + (char) 0xd800;
        JsonObject state = new JsonObject();
        state.addProperty("text", value);
        JsonElement normalized = StateJsonHelper.normalizeCardUuids(state);

        assertEquals(sha256Hex(normalized.toString()), SearchStateKey.fromJson(state).toString());
    }

    @Test
    public void ignoresJsonObjectFieldOrderButPreservesArrayOrder() {
        SearchStateKey first = key("{\"turn\":2,\"rng\":\"10\",\"cards\":[\"A\",\"B\"]}");
        SearchStateKey reordered = key("{\"cards\":[\"A\",\"B\"],\"rng\":\"10\",\"turn\":2}");
        SearchStateKey differentCardOrder = key("{\"cards\":[\"B\",\"A\"],\"rng\":\"10\",\"turn\":2}");

        assertEquals(first, reordered);
        assertEquals(first.hashCode(), reordered.hashCode());
        assertNotEquals(first, differentCardOrder);
    }

    @Test
    public void distinguishesOrderSensitiveCombatHistoryAndRng() {
        SearchStateKey attackThenSkill = key(readFixture("order_sensitive_a.json"));
        SearchStateKey skillThenAttack = key(readFixture("order_sensitive_b.json"));
        SearchStateKey differentRng = key(resourceLikeState("[\"Attack\",\"Skill\"]", "18"));

        assertNotEquals(attackThenSkill, skillThenAttack);
        assertNotEquals(attackThenSkill, differentRng);
    }

    @Test
    public void distinguishesCardInstanceState() {
        SearchStateKey normalCost = key("{\"hand\":[{\"id\":\"Strike_R\",\"cost_for_turn\":1,\"misc\":0}]}");
        SearchStateKey freeThisTurn = key("{\"hand\":[{\"id\":\"Strike_R\",\"cost_for_turn\":0,\"misc\":0}]}");

        assertNotEquals(normalCost, freeThisTurn);
    }

    @Test
    public void ignoresConcreteCardUuidsWhenReferenceTopologyMatches() {
        SearchStateKey client = key(cardHistory("10590ee7-4b45-477f-aa38-f94a3012e5de",
                "10590ee7-4b45-477f-aa38-f94a3012e5de"));
        SearchStateKey server = key(cardHistory("00000000-0000-0000-0000-0000000da991",
                "00000000-0000-0000-0000-0000000da991"));

        assertEquals(client, server);
    }

    @Test
    public void distinguishesDifferentCardReferenceTopology() {
        SearchStateKey sameCard = key(cardHistory("10590ee7-4b45-477f-aa38-f94a3012e5de",
                "10590ee7-4b45-477f-aa38-f94a3012e5de"));
        SearchStateKey differentCards = key(cardHistory("00000000-0000-0000-0000-0000000da991",
                "00000000-0000-0000-0000-0000000da992"));

        assertNotEquals(sameCard, differentCards);
    }

    private static SearchStateKey key(String json) {
        return SearchStateKey.fromJson(new JsonParser().parse(json));
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder encoded = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                encoded.append(String.format("%02x", current & 0xff));
            }
            return encoded.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String resourceLikeState(String playedCards, String rng) {
        return "{\"turn\":3,\"rng_state\":\"" + rng + "\","
                + "\"cards_played_this_turn\":" + playedCards + ","
                + "\"player_state\":{\"health\":40},"
                + "\"monster_state\":{\"health\":20}}";
    }

    private static String cardHistory(String turnUuid, String combatUuid) {
        return "{\"cards_played_this_turn_backup\":[{\"card_id\":\"Creative AI\",\"uuid\":\""
                + turnUuid + "\"}],\"cards_played_this_combat\":[{\"card_index\":-1,"
                + "\"card_state\":{\"card_id\":\"Creative AI\",\"uuid\":\"" + combatUuid + "\"}}]}";
    }

    private static String readFixture(String name) {
        String path = "/battle_states/" + name;
        InputStream stream = SearchStateKeyTest.class.getResourceAsStream(path);
        if (stream == null) {
            throw new AssertionError("Missing fixture " + path);
        }
        try (Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8.name())) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
}

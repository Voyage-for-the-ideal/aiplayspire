package battleaimod.search;

import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SearchStateKeyTest {
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

package battleaimod.search;

import com.google.common.hash.Hashing;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import savestate.StateJsonHelper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SearchStateKeyTest {
    @Test
    public void smallWritesAndAsciiFormattingMatchAcrossBufferEdges() {
        for (int padding = 8110; padding < 8220; padding++) {
            JsonObject state = new JsonObject();
            state.addProperty("a", String.join("", java.util.Collections.nCopies(padding, "x")));
            JsonArray values = new JsonArray();
            values.add(Long.MIN_VALUE); values.add(Double.MIN_VALUE); values.add(-0.0f);
            values.add(true); values.add(false); values.add((String) null);
            values.add("cached ASCII value"); values.add("雪\\\"\n\u2028");
            state.add("b", values);
            state.addProperty("c", Long.MAX_VALUE);
            assertReference(state);
        }
    }

    @Test
    public void integralWrappersPreserveExactSpellingAcrossLimitsAndBufferBoundaries() {
        JsonArray values = new JsonArray();
        Number[] limits = {Byte.MIN_VALUE, Byte.MAX_VALUE, Short.MIN_VALUE, Short.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE,
                -100L, -10L, -1L, 0L, 9L, 10L, 100L};
        for (int i = 0; i < 200; i++) for (Number value : limits) values.add(value);
        // Float, decimal scale and parsed numeric lexemes keep their own spelling.
        values.add(-0.0f); values.add(-0.0d);
        values.add(new java.math.BigDecimal("1.2300"));
        values.add(new java.math.BigInteger("123456789012345678901234567890"));
        values.add(new JsonParser().parse("1e+20"));
        assertReference(values);
        values.set(0, new com.google.gson.JsonPrimitive(Long.MAX_VALUE));
        assertReference(values);
    }

    @Test
    public void cachedFieldNamesMatchEscapedUnicodeAndOversizedNames() {
        JsonObject state = new JsonObject();
        String[] names = {"plain", "<>&='", "\\\"\n\t", "雪\u2028\u2029",
                new String(Character.toChars(0x1f603)), "lone-" + (char) 0xd800,
                String.join("", java.util.Collections.nCopies(9000, "x"))};
        for (int i = 0; i < names.length; i++) state.addProperty(names[i], i);
        assertReference(state);
        state.addProperty(names[2], Long.MIN_VALUE);
        assertReference(state);
        state.remove(names[0]);
        state.addProperty("new-schema", 12);
        assertReference(state);
    }

    @Test
    public void cachesNeverHideMutationOrSchemaChanges() {
        JsonObject state = new JsonObject();
        state.addProperty("hp", 40);
        SearchStateKey original = SearchStateKey.fromJson(state);
        state.addProperty("hp", 41);
        assertNotEquals(original, SearchStateKey.fromJson(state));
        state.remove("hp");
        state.addProperty("mp", 40);
        assertNotEquals(original, SearchStateKey.fromJson(state));
        state.remove("mp");
        state.addProperty("hp", 40);
        assertEquals(original, SearchStateKey.fromJson(state));
    }

    @Test
    public void matchesReferenceAcrossMixedMutableStatesAndCacheSaturation() {
        Random random = new Random(417);
        for (int i = 0; i < 2000; i++) {
            JsonObject state = new JsonObject();
            // Exceed both metadata-cache capacities without changing semantics.
            state.addProperty("extension-" + i, "value-" + i);
            state.add("nested", randomJson(random, 3));
            state.addProperty("uuid", "instance-" + (i % 7));
            state.addProperty("text", i % 2 == 0 ? "normal card text" : "\\\"\n雪\u2028");
            assertReference(state);
            state.addProperty("extension-" + i, i);
            assertReference(state);
        }
        for (String number : new String[]{"-0.0", "1e+20", "1.2300", "123456789012345678901234567890", "true", "false", "null"}) {
            assertReference(new JsonParser().parse(number));
        }
    }

    @Test
    public void metadataCachesAreThreadLocal() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int thread = 0; thread < 4; thread++) {
                final int seed = thread;
                results.add(executor.submit(() -> {
                    Random random = new Random(seed);
                    for (int i = 0; i < 100; i++) assertReference(randomJson(random, 3));
                }));
            }
            for (Future<?> result : results) result.get();
        } finally {
            executor.shutdownNow();
        }
    }

    private static void assertReference(JsonElement state) {
        assertEquals(murmur3Hex(StateJsonHelper.normalizeCardUuids(state).toString()),
                     SearchStateKey.fromJson(state).toString());
    }

    private static JsonElement randomJson(Random random, int depth) {
        if (depth == 0) {
            return new JsonParser().parse(random.nextBoolean() ? "-0.0" : "123.456");
        }
        if (random.nextBoolean()) {
            JsonArray array = new JsonArray();
            for (int i = 0; i < 3; i++) array.add(randomJson(random, depth - 1));
            return array;
        }
        JsonObject object = new JsonObject();
        object.addProperty("uuid", "instance-" + random.nextInt(5));
        object.addProperty("label", "card-" + random.nextInt(50));
        for (int i = 0; i < 3; i++) object.add("field-" + random.nextInt(8), randomJson(random, depth - 1));
        return object;
    }

    @Test
    public void preservesMurmur3Digests() {
        assertEquals("a996b5ff006e386ae5fde91de647314d",
                key("{\"turn\":2,\"rng\":\"10\",\"cards\":[\"A\",\"B\"]}").toString());
        assertEquals("d39c999cb0fd2ff41e9c6e44584a17f5",
                key("{\"z\":null,\"unicode\":\"\\u96ea\\n\\\"\",\"number\":1.25,\"bool\":true,"
                        + "\"array\":[3,\"x\"],\"card\":{\"uuid\":\"client-uuid\",\"id\":\"A\"}}")
                        .toString());
        assertEquals("cd90ca36a54ae9de66f7474e2fcca90a",
                key(cardHistory("client-uuid", "client-uuid")).toString());
    }

    @Test
    public void rendersLowercaseMurmur3Hex() {
        String encoded = key("{\"turn\":1}").toString();
        SearchStateKey key = key("{\"turn\":1}");
        String firstEncoding = key.toString();

        assertEquals("murmur3_128", SearchStateKey.algorithm());
        assertEquals(32, encoded.length());
        assertTrue(encoded.matches("[0-9a-f]{32}"));
        assertSame(firstEncoding, key.toString());
    }

    @Test
    public void matchesGsonEncodingForEscapedAndUnicodeStrings() {
        String value = "<>&='\\\"\t\b\n\r\f" + (char) 0x01 + (char) 0x2028 + (char) 0x2029
                + new String(Character.toChars(0x1f603)) + (char) 0xd800;
        JsonObject state = new JsonObject();
        state.addProperty("text", value);
        JsonElement normalized = StateJsonHelper.normalizeCardUuids(state);

        assertEquals(murmur3Hex(normalized.toString()), SearchStateKey.fromJson(state).toString());
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

    private static String murmur3Hex(String value) {
        return Hashing.murmur3_128().hashString(value, StandardCharsets.UTF_8).toString();
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

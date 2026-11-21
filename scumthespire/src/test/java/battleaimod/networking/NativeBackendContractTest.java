package battleaimod.networking;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.megacrit.cardcrawl.random.Random;
import ludicrousspeed.LudicrousSpeedMod;
import ludicrousspeed.simulator.commands.Command;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class NativeBackendContractTest {
    private static final Path ROOT = Paths.get("..").toAbsolutePath().normalize();

    @Test public void unknownSelectorStopsBeforeDispatch() throws Exception {
        JsonObject reply = new JsonObject();
        try { AiClient.requireVerifiableOutcome(reply); fail("dispatched without a postcondition"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("not executed")); }
        reply.add("expected_after", new JsonObject());
        reply.addProperty("expected_screen","GRID");
        AiClient.requireVerifiableOutcome(reply);
        reply = new JsonObject(); reply.addProperty("terminal_prediction","victory");
        AiClient.requireVerifiableOutcome(reply);
    }

    private JsonObject fixture() throws IOException {
        byte[] bytes = Files.readAllBytes(ROOT.resolve("battle_engine/tests/fixtures/ironclad.json"));
        return new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private JsonObject worker(JsonObject snapshot, String operation) throws Exception {
        return worker(snapshot,operation,new JsonObject());
    }

    private JsonObject worker(JsonObject snapshot, String operation, JsonObject extra) throws Exception {
        Path binary = Paths.get(System.getProperty("battleai.nativeWorker", ROOT.resolve("battle_engine/bin/battle-engine-worker.exe").toString()));
        assumeTrue("Build battle_engine/build.ps1 for native contract tests", Files.isRegularFile(binary));
        JsonObject request = new JsonObject();
        request.add("snapshot", snapshot); request.addProperty("operation", operation);
        request.addProperty("max_expansions", 100); request.addProperty("timeout_ms", 50);
        for (java.util.Map.Entry<String,com.google.gson.JsonElement> entry:extra.entrySet()) request.add(entry.getKey(),entry.getValue());
        Process process = new ProcessBuilder(binary.toString()).start();
        try {
            try (Writer writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(request.toString());
            }
            assertTrue("worker timed out", process.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue());
            JsonObject result = new JsonParser().parse(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(result.toString(), result.get("supported").getAsBoolean());
            return result;
        } finally { process.destroyForcibly(); }
    }

    @Test public void nativeAndJavaProjectionsMatch() throws Exception {
        JsonObject source = fixture();
        assertEquals(BattleStateProjection.fromSnapshot(source), worker(source, "import").get("projection"));
    }

    @Test public void foreignInfluenceMatchesBothJavaRngStreams() throws Exception {
        JsonObject state=fixture(), player=state.getAsJsonObject("player_state");
        player.addProperty("chosen_class_name","WATCHER");
        JsonArray hand=new JsonArray();JsonObject card=new JsonObject();
        card.addProperty("card_id","ForeignInfluence");card.addProperty("cost",0);card.addProperty("cost_for_turn",0);
        card.addProperty("times_upgraded",1);hand.add(card);player.add("hand",hand);
        player.add("draw_pile",new JsonArray());player.add("relics",new JsonArray());
        state.getAsJsonObject("rng_state").addProperty("card_rng_counter",0);
        JsonObject pools=new JsonObject();
        for(String key:new String[]{"any","attacks","skills","powers","colorless","common","uncommon","rare"}) {
            JsonArray ids=new JsonArray();ids.add("Strike_R");pools.add(key,ids);
        }
        String[][] candidates={{"Cleave","Cold Snap","Slice"},{"Dash","Uppercut","WheelKick"},{"Glass Knife","Meteor Strike","Ragnarok"}};
        String[] names={"common","uncommon","rare"};
        for(int i=0;i<3;++i){JsonArray ids=new JsonArray();for(String id:candidates[i])ids.add(id);pools.add("library_attacks_"+names[i],ids);}
        state.add("_combat_card_pools",pools);
        long seed=state.getAsJsonObject("rng_state").get("seed").getAsLong();
        Random random=new Random(seed+state.get("floor_num").getAsInt(),0), cards=new Random(seed,0);
        java.util.List<String> expected=new java.util.ArrayList<>();
        while(expected.size()<3){int roll=random.random(99), rarity=roll<55?0:roll<85?1:2;random.randomLong();
            String id=candidates[rarity][cards.random(candidates[rarity].length-1)];if(!expected.contains(id))expected.add(id);}
        JsonObject extra=new JsonObject();extra.addProperty("card_index",0);
        JsonObject actual=worker(state,"step",extra);
        JsonArray options=actual.getAsJsonObject("selection").getAsJsonArray("options");
        for(int i=0;i<3;++i)assertEquals(expected.get(i),options.get(i).getAsJsonObject().get("id").getAsString());
        assertEquals(cards.counter,actual.get("card_rng_counter").getAsInt());
        assertEquals(random.counter,actual.getAsJsonObject("projection").getAsJsonObject("rng").get("card_random_rng_counter").getAsInt());
    }

    @Test public void selectionContextIncludesTypedContinuationAndSelectedCards() throws Exception {
        JsonObject state=fixture();state.addProperty("is_screen_up",true);state.addProperty("screen_name","HAND_SELECT");
        JsonObject view=new JsonObject();view.add("action_queue",new JsonArray());view.add("selected_cards",new JsonArray());
        state.add("hand_select_screen_state",view);
        JsonObject before=BattleStateProjection.context(state);
        view.getAsJsonArray("selected_cards").add("changed");
        assertNotEquals(before,BattleStateProjection.context(state));
        assertEquals("HAND_SELECT",before.getAsJsonObject("selection").get("screen").getAsString());
    }

    @Test public void nativeSourceContextMatchesJava() throws Exception {
        JsonObject source = fixture();
        JsonObject result = worker(source, "search");
        assertEquals(BattleStateProjection.context(source), result.getAsJsonArray("commands")
                .get(0).getAsJsonObject().get("expected_context"));
    }

    @Test public void javaModifiedUtfUsesUtf16Surrogates() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new DataOutputStream(bytes).writeUTF("\u0000\ud83d\ude00");
        assertArrayEquals(new byte[]{0, 8, (byte)0xc0, (byte)0x80, (byte)0xed, (byte)0xa0,
                (byte)0xbd, (byte)0xed, (byte)0xb8, (byte)0x80}, bytes.toByteArray());
    }

    @Test public void sixRngStreamsMatchRealJavaRandom() throws Exception {
        String[] names = {"ai_rng_counter", "card_random_rng_counter", "misc_rng_counter", "monster_hp_rng_counter", "potion_rng_counter", "shuffle_rng_counter"};
        for (long seed : new long[]{0L, 12345L, Long.MAX_VALUE, -1L}) {
            JsonObject source = fixture(), rng = source.getAsJsonObject("rng_state");
            source.addProperty("floor_num", 17); rng.addProperty("seed", seed);
            for (int i = 0; i < names.length; ++i) { rng.addProperty(names[i], i * 37); }
            JsonArray samples = worker(source, "rng_probe").getAsJsonArray("samples");
            for (int i = 0; i < names.length; ++i) {
                Random reference = new Random(i == 4 ? seed : seed + 17, i * 37);
                for (int j = 0; j < 16; ++j) {
                    assertEquals("seed=" + seed + " stream=" + names[i] + " offset=" + j,
                            reference.random(999), samples.get(i).getAsJsonArray().get(j).getAsInt());
                }
            }
        }
    }

    @Test public void guardChecksBeforeMutating() {
        AtomicInteger executions = new AtomicInteger();
        Command delegate = new Command() {
            public void execute() { executions.incrementAndGet(); }
            public String encode() { return "{\"type\":\"END\"}"; }
        };
        JsonObject expected = new JsonObject(); expected.addProperty("format", 1); expected.addProperty("hp", 70);
        LudicrousSpeedMod.mustRestart = false;
        try {
            new GuardedCommand(delegate, expected, () -> expected).execute();
            assertEquals(1, executions.get());
            JsonObject different = new JsonObject(); different.addProperty("format", 1); different.addProperty("hp", 69);
            new GuardedCommand(delegate, expected, () -> different).execute();
            assertEquals(1, executions.get()); assertTrue(LudicrousSpeedMod.mustRestart);
        } finally { LudicrousSpeedMod.mustRestart = false; }
    }

    @Test public void embeddedJniAndProcessAgreeAndSessionsCloseIndependently() throws Exception {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        JsonObject state = fixture(), request = new JsonObject();
        request.add("snapshot", state);
        request.addProperty("operation", "fingerprint");
        try (NativeEngine first = new NativeEngine(); NativeEngine second = new NativeEngine()) {
            assertEquals(worker(state, "fingerprint"), first.search(request));
            assertEquals(first.search(request), second.search(request));
            first.close();
            assertThrows(IllegalStateException.class, () -> first.search(request));
            assertThrows(RuntimeException.class, () -> second.search(new JsonObject()));
            request.addProperty("operation", "search");
            request.addProperty("max_expansions", 1);
            request.addProperty("timeout_ms", 2000);
            JsonObject result = second.search(request);
            assertTrue(result.get("supported").getAsBoolean());
            assertTrue(result.getAsJsonArray("commands").get(0).getAsJsonObject().has("expected_context"));
        }
    }

    @Test public void nativeProfilesHaveIndependentJavaBudgets() {
        assertEquals(250L, battleaimod.search.SearchProfile.FAST.nativeTimeoutMillis());
        assertEquals(2000L, battleaimod.search.SearchProfile.BALANCED.nativeTimeoutMillis());
        assertEquals(10000L, battleaimod.search.SearchProfile.DEEP.nativeTimeoutMillis());
        assertEquals(10000L, battleaimod.search.SearchProfile.FAST.timeoutMillis());
        assertEquals(90000L, battleaimod.search.SearchProfile.BALANCED.timeoutMillis());
        assertEquals(90000L, battleaimod.search.SearchProfile.DEEP.timeoutMillis());
    }

    @Test public void explicitRunBudgetAndSeedAreOptIn() {
        String oldBudget = System.getProperty("battleai.clientBudgetMs"), oldSeed = System.getProperty("battleai.runSeed");
        try {
            System.clearProperty("battleai.clientBudgetMs"); System.clearProperty("battleai.runSeed");
            assertEquals(30000, BackendEndpoint.timeoutMillis(30000)); assertNull(BackendEndpoint.runSeed());
            System.setProperty("battleai.clientBudgetMs", "2000"); System.setProperty("battleai.runSeed", "CPPBENCH01");
            assertEquals(2000, BackendEndpoint.timeoutMillis(30000)); assertNotNull(BackendEndpoint.runSeed());
            System.setProperty("battleai.clientBudgetMs", "0");
            assertThrows(IllegalArgumentException.class, () -> BackendEndpoint.timeoutMillis(30000));
        } finally {
            if (oldBudget == null) System.clearProperty("battleai.clientBudgetMs"); else System.setProperty("battleai.clientBudgetMs", oldBudget);
            if (oldSeed == null) System.clearProperty("battleai.runSeed"); else System.setProperty("battleai.runSeed", oldSeed);
        }
    }
}

package battleaimod.networking;

import battleaimod.BattleAiMod;
import battleaimod.battleai.CommandRunnerController;
import battleaimod.search.SearchProfile;
import com.google.gson.*;
import ludicrousspeed.LudicrousSpeedMod;
import ludicrousspeed.simulator.commands.Command;
import savestate.SaveState;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.*;

/** The game thread owns current, the runner, snapshots and all decisions to apply a result.
 * The executor sees only detached JSON. Closing is queued behind any outstanding JNI call. */
public final class AiClient {
    private static final ExecutorService planner = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "battle-native-planner"); t.setDaemon(true); return t;
    });
    private static AiClient current;
    private final String session = UUID.randomUUID().toString();
    private NativeEngine engine; // planner thread only
    private Future<JsonObject> pending;
    private JsonObject request, reply, actual;
    private boolean executed;
    private boolean captured;
    private long terminalWaitStart;
    private static void trace(String kind, JsonObject data) {
        String path = System.getProperty("battleai.trace");
        if (path == null) return;
        JsonObject event = new JsonObject(); event.addProperty("kind", kind);
        for (java.util.Map.Entry<String,JsonElement> e : data.entrySet()) event.add(e.getKey(), e.getValue());
        try { Files.write(Paths.get(path), (event.toString()+"\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (IOException e) { System.err.println("Native trace: " + e); }
    }
    private final java.util.ArrayDeque<JsonObject> suffix = new java.util.ArrayDeque<>();

    public static boolean active() { return current != null; }
    public static void start() { if (current == null) current = new AiClient(); }
    public static void stop() {
        AiClient old = current;
        current = null;
        if (BattleAiMod.rerunController != null) {
            BattleAiMod.rerunController.cancel();
            if (LudicrousSpeedMod.controller == BattleAiMod.rerunController) LudicrousSpeedMod.controller = null;
            BattleAiMod.rerunController = null;
        }
        LudicrousSpeedMod.mustRestart = false;
        if (old != null) planner.execute(() -> { if (old.engine != null) old.engine.close(); });
    }

    public static void tick(boolean inCombat) {
        AiClient client = current;
        if (client == null) return;
        try {
            // The victory cleanup can remove the combat screen before a new decision point.
            boolean ended = AbstractDungeon.currMapNode != null && AbstractDungeon.getCurrRoom() != null &&
                    (AbstractDungeon.getCurrRoom().isBattleOver || AbstractDungeon.player != null && AbstractDungeon.player.isDead);
            if (ended && client.executed && client.reply != null && client.reply.has("terminal_prediction")) {
                String actualOutcome = AbstractDungeon.player.currentHealth <= 0 ? "defeat" : "victory";
                if (!actualOutcome.equals(client.reply.get("terminal_prediction").getAsString()))
                    throw new IOException("native terminal prediction mismatch");
                JsonObject result=new JsonObject(); result.addProperty("outcome",actualOutcome);
                result.addProperty("hp", AbstractDungeon.player.currentHealth);
                trace("terminal",result); stop(); return;
            }
            if (ended && client.executed) throw new IOException("unexpected real battle terminal");
            if (!inCombat || ended) { stop(); return; }
            if (LudicrousSpeedMod.mustRestart) throw new IllegalStateException("command precondition mismatch");
            if (BattleAiMod.rerunController != null && !BattleAiMod.rerunController.isDone()) return;
            if (BattleClientController.readyForUpdate()) client.advance();
        } catch (Exception | LinkageError e) {
            client.fail(e);
        }
    }

    static void requireVerifiableOutcome(JsonObject planned) throws IOException {
        if (planned.has("terminal_prediction")) {
            String outcome = planned.get("terminal_prediction").getAsString();
            if ("victory".equals(outcome) || "defeat".equals(outcome)) return;
            throw new IOException("unknown native terminal prediction");
        }
        if (!planned.has("expected_after") || !planned.get("expected_after").isJsonObject()
                || !planned.has("expected_screen") || planned.get("expected_screen").isJsonNull())
            throw new IOException("unsupported post-action boundary; command not executed");
    }

    private void advance() throws Exception {
        if (pending != null) {
            if (!pending.isDone()) return;
            reply = pending.get(); pending = null;
            if (!reply.get("supported").getAsBoolean()) throw new IOException(reply.toString());
            if (reply.has("continuations")) {
                suffix.clear();
                for (JsonElement step : reply.getAsJsonArray("continuations")) suffix.add(step.getAsJsonObject());
            }
            JsonObject event = new JsonObject();
            event.add("metrics", reply.get("metrics")); event.addProperty("build", NativeEngine.buildId());
            if (reply.has("commands") && reply.getAsJsonArray("commands").size()==1)
                event.add("command", reply.getAsJsonArray("commands").get(0).getAsJsonObject().get("command"));
            trace("response", event);
            requireVerifiableOutcome(reply);
            JsonArray commands = reply.getAsJsonArray("commands");
            if (commands.size() != 1) throw new IOException("expected one guarded command");
            JsonObject envelope = commands.get(0).getAsJsonObject();
            if (!envelope.has("expected_before") || !envelope.has("expected_context"))
                throw new IOException("missing native command guards");
            Command cmd = CommandCodec.decode(envelope);
            if (cmd == null) throw new IOException("empty native command");
            BattleAiMod.rerunController = new CommandRunnerController(Collections.singletonList(cmd), true);
            LudicrousSpeedMod.controller = BattleAiMod.rerunController;
            executed = true;
            return;
        }
        if (executed && reply.has("terminal_prediction")) {
            // The killing blow is applied before death animations and room cleanup.
            // Do not compare that transient frame with a normal player decision point.
            if (terminalWaitStart == 0) terminalWaitStart = System.nanoTime();
            if (System.nanoTime()-terminalWaitStart < TimeUnit.SECONDS.toNanos(15)) return;
            throw new IOException("native terminal prediction not observed");
        }
        actual = BattleStateProjection.normalize(new SaveState().jsonEncode()).getAsJsonObject();
        if (executed) {
            String screen = actual.get("is_screen_up").getAsBoolean() ? actual.get("screen_name").getAsString() : "NONE";
            if (!reply.has("expected_after") || !reply.get("expected_after").equals(BattleStateProjection.fromSnapshot(actual)) ||
                    !screen.equals(reply.get("expected_screen").getAsString()))
                throw new IOException("native post-action state mismatch or unverified boundary");
            verifyHeadbuttOutcome(actual, reply);
            verifyGridOutcome(actual, reply);
            trace("applied", new JsonObject());
            executed = false;
        }
        actual.add("_combat_card_pools", BattleCardPools.current());
        SearchProfile profile = BattleAiMod.searchProfile;
        JsonObject next = new JsonObject();
        next.add("snapshot", actual);
        next.addProperty("session_id", session);
        next.addProperty("search_policy", System.getProperty("battleai.searchPolicy", "legacy"));
        next.addProperty("tactical_ordering", Boolean.parseBoolean(System.getProperty("battleai.tacticalOrdering", "true")));
        next.addProperty("search_profile", profile.name());
        int cap = Integer.getInteger("battleai.maxExpansions", profile.maxExpansions());
        if (cap < 1 || cap > 1000000) throw new IllegalArgumentException("Invalid expansion budget");
        next.addProperty("max_expansions", cap);
        next.addProperty("timeout_ms", BackendEndpoint.timeoutMillis(profile.nativeTimeoutMillis()));
        int length = Integer.getInteger("battleai.segmentLength", 1);
        next.addProperty("segment_length", length);
        next.addProperty("dedup_mode", System.getProperty("battleai.dedupMode", "default"));
        // Hashes refer to this immutable observation. Compare configuration as well as state:
        // changing the player's profile cancels the remainder of the previous plan.
        if (request != null) {
            for (String key : new String[]{"timeout_ms","max_expansions","search_profile","search_policy","tactical_ordering","segment_length","dedup_mode"})
                if (!request.get(key).equals(next.get(key))) suffix.clear();
            if (!request.getAsJsonObject("snapshot").get("_combat_card_pools").equals(actual.get("_combat_card_pools"))) suffix.clear();
        }
        final JsonObject candidate = suffix.poll();
        request = next;
        if (!captured && Boolean.getBoolean("battleai.capture")) {
            Path directory = Files.createDirectories(Paths.get("startstates",session));
            Files.write(directory.resolve("start.txt"), actual.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
            captured = true;
        }
        JsonObject audit = new JsonObject();
        for (String key : new String[]{"search_policy","tactical_ordering","search_profile","max_expansions","timeout_ms","segment_length","dedup_mode"})
            audit.add(key,next.get(key));
        audit.addProperty("session",session); trace("request",audit);
        reply = null;
        pending = planner.submit(() -> {
            if (engine == null) engine = new NativeEngine();
            if (candidate != null && matches(next.getAsJsonObject("snapshot"), candidate)) return candidate;
            JsonObject planned = engine.search(next);
            if (!planned.has("continuations")) planned.add("continuations", new JsonArray());
            return planned;

        });
    }

    private boolean matches(JsonObject snapshot, JsonObject step) {
        JsonObject envelope = step.getAsJsonArray("commands").get(0).getAsJsonObject();
        if (!envelope.get("expected_context").equals(BattleStateProjection.context(snapshot))
                || !envelope.get("expected_before").equals(BattleStateProjection.fromSnapshot(snapshot))) return false;
        JsonObject player = snapshot.getAsJsonObject("player_state");
        if (!pileIds(player).equals(step.get("required_uuids"))) return false;
        JsonObject query = new JsonObject();
        query.addProperty("operation", "fingerprint"); query.add("snapshot", snapshot);
        JsonObject observed = engine.search(query);
        return step.get("required_native_key").equals(observed.get("native_key")) &&
               step.get("required_path_context").equals(observed.get("path_context"));
    }

    private static JsonObject pileIds(JsonObject player) {
        JsonObject ids = new JsonObject();
        for (String pile : new String[]{"hand","draw_pile","discard_pile","exhaust_pile"}) {
            JsonArray values = new JsonArray();
            for (JsonElement card : player.getAsJsonArray(pile)) values.add(card.getAsJsonObject().get("uuid"));
            ids.add(pile, values);
        }
        return ids;
    }

    static void verifyHeadbuttOutcome(JsonObject snapshot, JsonObject planned) throws IOException {
        if (!planned.has("expected_desktop_headbutt_uuids")) return;
        JsonObject player = snapshot.getAsJsonObject("player_state");
        if (!pileIds(player).equals(planned.get("expected_desktop_headbutt_uuids")))
            throw new IOException("Headbutt post-action card identity mismatch");
        if (planned.has("expected_desktop_headbutt_pending_uuid")) {
            JsonElement card = player.get("card_in_use");
            if (card == null || !card.isJsonObject() ||
                    !planned.get("expected_desktop_headbutt_pending_uuid").equals(card.getAsJsonObject().get("uuid")))
                throw new IOException("Headbutt pending card identity mismatch");
        }
    }

    static void verifyGridOutcome(JsonObject snapshot, JsonObject planned) throws IOException {
        JsonObject player = snapshot.getAsJsonObject("player_state");
        if (planned.has("expected_desktop_grid_selected_uuids")) {
            JsonObject view = snapshot.getAsJsonObject("grid_card_select_screen_state");
            JsonArray indexed = new JsonArray(), selected = new JsonArray();
            for (String pile : new String[]{"master_deck","draw_pile","hand","discard_pile","exhaust_pile","limbo"})
                indexed.addAll(player.getAsJsonArray(pile));
            for (JsonElement entry : view.getAsJsonArray("selected_cards")) {
                JsonObject container = entry.getAsJsonObject();
                JsonElement card = container.get("card_state");
                if (card == null || card.isJsonNull()) card = indexed.get(container.get("card_index").getAsInt());
                selected.add(card.getAsJsonObject().get("uuid"));
            }
            if (!planned.get("expected_desktop_grid_selected_uuids").equals(selected))
                throw new IOException("Grid selection selected card identity mismatch");
        }
        if (planned.has("expected_desktop_hand_selected_uuids")) {
            JsonArray selected=new JsonArray();
            for (JsonElement card : snapshot.getAsJsonObject("hand_select_screen_state").getAsJsonArray("selected_cards"))
                selected.add(card.getAsJsonObject().get("uuid"));
            if (!selected.equals(planned.get("expected_desktop_hand_selected_uuids")))
                throw new IOException("Hand selection card identity mismatch");
        }
        if (planned.has("expected_desktop_grid_uuids")) {
            JsonObject actualIds=pileIds(player), expectedIds=planned.getAsJsonObject("expected_desktop_grid_uuids");
            for (java.util.Map.Entry<String,JsonElement> entry : expectedIds.entrySet()) {
                JsonArray actualPile = actualIds.getAsJsonArray(entry.getKey()), forecast = entry.getValue().getAsJsonArray();
                if (actualPile.size()!=forecast.size()) throw new IOException("Grid selection changed pile size");
                for (int i=0; i<forecast.size(); i++)
                    if (!forecast.get(i).isJsonNull() && !forecast.get(i).equals(actualPile.get(i)))
                        throw new IOException("Grid selection post-action card identity mismatch");
            }
        }
    }

    private void fail(Throwable error) {
        Throwable cause = error instanceof ExecutionException && error.getCause() != null ? error.getCause() : error;
        BattleAiMod.steveMessage = "Auto battle stopped: " + cause.getMessage();
        System.err.println("BATTLE_NATIVE_STOP " + cause);
        cause.printStackTrace();
        JsonObject failure = new JsonObject(); failure.addProperty("error",cause.toString()); trace("error",failure);
        try {
            JsonObject evidence = new JsonObject();
            evidence.addProperty("error", cause.toString());
            evidence.addProperty("worker_sha256", NativeEngine.buildId());
            evidence.add("request", request); evidence.add("response", reply);
            try { evidence.add("actual", new SaveState().jsonEncode()); }
            catch (RuntimeException unavailable) { evidence.add("actual", actual); }
            Path dir = Files.createDirectories(Paths.get("battle-native-diagnostics"));
            Files.write(dir.resolve(session + ".json"), evidence.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception io) { System.err.println("Battle diagnostic: " + io); }
        finally { stop(); }
    }
}

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.megacrit.cardcrawl.map.MapEdge;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import com.megacrit.cardcrawl.ui.campfire.AbstractCampfireOption;
import com.megacrit.cardcrawl.ui.campfire.SmithOption;
import com.megacrit.cardcrawl.ui.campfire.RecallOption;
import com.megacrit.cardcrawl.ui.campfire.LiftOption;
import com.megacrit.cardcrawl.ui.campfire.DigOption;
import com.megacrit.cardcrawl.ui.campfire.TokeOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/** Local map/campfire objects + pure control facts. Actual engine stays BOOT. */
public final class FlowExportFixtureTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static Map<String, Object> flow(String dungeon) {
        return HeadlessInteractionExport.object("schema", HeadlessFlowExport.SCHEMA, "dungeon", dungeon,
                "path", HeadlessInteractionExport.list(HeadlessInteractionExport.object("x", 1, "y", 0)),
                "flight", false, "final_act_available", true, "campfire_history", new ArrayList<>(),
                "bosses_remaining", 1, "player_dead", false, "victory", false,
                "result_screen", "NONE", "terminal", null, "route", "none");
    }
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[2]);
        InteractionExportFixtureTest.main(new String[] {args[0], args[1], output.resolveSibling("interaction-fixtures.json").toString()});
        Gson gson = new Gson();
        JsonObject base = JsonParser.parseString(Files.readString(output.resolveSibling("base-state-fixture.json"))).getAsJsonObject();
        MapRoomNode a = new MapRoomNode(1, 0), b = new MapRoomNode(1, 1), c = new MapRoomNode(3, 0), d = new MapRoomNode(3, 1);
        for (var n : Arrays.asList(a,b,c,d)) n.room = new MonsterRoom();
        a.addEdge(new MapEdge(1, 0, 1, 1)); c.addEdge(new MapEdge(3, 0, 3, 1));
        var rows = Arrays.asList(Arrays.asList(a,c), Arrays.asList(b,d));
        var options = Arrays.<AbstractCampfireOption>asList(new SmithOption(false), new RecallOption(), new LiftOption(true), new DigOption(), new TokeOption(true));
        var optionRows = new ArrayList<>();
        for (var o : options) optionRows.add(HeadlessFlowExport.campfireOption(o.getClass().getSimpleName(), optionRows.size(), o.usable));
        var choices = HeadlessFlowExport.mapChoices(rows, a, true, 2, false, 1);
        check(choices.size() == 2, "normal and winged choice");
        var rng = (RandomXS128) MathUtils.random; long r0 = rng.getState(0), r1 = rng.getState(1);
        String stable = gson.toJson(choices);
        for (int i = 0; i < 200; i++) {
            check(stable.equals(gson.toJson(HeadlessFlowExport.mapChoices(rows, a, true, 2, false, 1))), "map choices unstable");
            check(new HeadlessFlowKernel().phase() == com.megacrit.cardcrawl.headless.GameKernel.Phase.BOOT, "flow kernel started run");
            check("BOOT".equals(HeadlessInteractionExport.capture(NoRunGuard.kernel()).get("phase")), "export started run");
        }
        int cases = 0;
        for (boolean dead : new boolean[] {false,true}) for (boolean victory : new boolean[] {false,true})
            for (String screen : new String[] {"NONE","DEATH","VICTORY"}) for (int act : new int[] {1,3,4}) {
                String room = act == 4 ? "TrueVictoryRoom" : act == 3 ? "VictoryRoom" : "MonsterRoom";
                var t = HeadlessFlowExport.terminal(dead, room, screen, victory, screen.equals("DEATH"), screen.equals("VICTORY"), act);
                boolean expected = screen.equals("DEATH") && dead && (!victory || act == 3) || act == 4 && screen.equals("VICTORY") && victory;
                check((t != null) == expected, "terminal truth table"); cases++;
            }
        JsonObject map = base.deepCopy();
        map.addProperty("phase", "MAP"); map.addProperty("roomPhase", "COMPLETE");
        map.add("map", gson.toJsonTree(HeadlessStateExport.map(rows, a)));
        map.getAsJsonObject("player").add("relics", gson.toJsonTree(HeadlessInteractionExport.list(HeadlessInteractionExport.object("id", "WingedGreaves", "name", "fixture", "counter", 2))));
        map.add("interaction", gson.toJsonTree(HeadlessInteractionExport.object("schema", HeadlessInteractionExport.SCHEMA,
                "flow", flow("Exordium"), "map_choices", choices)));
        JsonObject camp = base.deepCopy();
        camp.addProperty("phase", "CAMPFIRE"); camp.addProperty("room", "RestRoom"); camp.addProperty("roomPhase", "INCOMPLETE"); camp.add("map", null);
        camp.add("interaction", gson.toJsonTree(HeadlessInteractionExport.object("schema", HeadlessInteractionExport.SCHEMA,
                "flow", flow("Exordium"), "campfire", HeadlessInteractionExport.object("selected", false, "options", optionRows))));
        var routes = HeadlessInteractionExport.object("boss_chest", HeadlessFlowExport.route("REWARD", "MonsterRoomBoss", "Exordium", 10, 1, ""),
                "next_act", HeadlessFlowExport.route("PROCEED", "TreasureRoomBoss", "TheCity", 10, 1, ""),
                "second_boss", HeadlessFlowExport.route("PROCEED", "MonsterRoomBoss", "TheBeyond", 20, 2, ""),
                "heart_event", HeadlessFlowExport.route("PROCEED", "MonsterRoomBoss", "TheBeyond", 20, 1, ""),
                "true_victory", HeadlessFlowExport.route("PROCEED", "MonsterRoomBoss", "TheEnding", 20, 1, ""));
        for (var entry : routes.entrySet()) check(entry.getKey().equals(entry.getValue()), "route mismatch");
        Files.writeString(output, gson.toJson(HeadlessInteractionExport.object("map", map, "campfire", camp, "routes", routes,
                "terminal_truth_cases", cases, "heart_terminal", HeadlessFlowExport.terminal(false,"TrueVictoryRoom","VICTORY",true,false,true,4))));
        check(a.getEdges().size() == 1 && !a.taken && b.getEdges().isEmpty(), "local nodes mutated");
        check(rng.getState(0) == r0 && rng.getState(1) == r1, "flow reads consumed RNG");
        NoRunGuard.verify();
        System.out.println("FLOW_EXPORT_FIXTURE_OK reads=200 terminal_cases=" + cases + " native_actions=0 run_started=false rng_unchanged=true");
    }
}

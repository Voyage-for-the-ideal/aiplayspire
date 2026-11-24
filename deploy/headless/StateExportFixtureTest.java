import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.google.gson.Gson;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.red.SearingBlow;
import com.megacrit.cardcrawl.headless.GameKernel;
import com.megacrit.cardcrawl.map.MapEdge;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoomElite;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Real card/map objects as local fixtures, with no player/dungeon/newRun. */
public final class StateExportFixtureTest {
    private static void check(boolean value, String text) {
        if (!value) throw new AssertionError(text);
    }

    public static void main(String[] args) throws Exception {
        GameKernel.boot(args[0], args[1]);
        NoRunGuard.verify();
        SearingBlow upgraded = new SearingBlow();
        upgraded.uuid = UUID.fromString("00000000-0000-4000-8000-000000000001");
        upgraded.timesUpgraded = 3;
        upgraded.upgraded = true;
        upgraded.baseDamage = 27;
        upgraded.damage = 99;
        upgraded.costForTurn = 0;
        upgraded.name = "fixture localized name without upgrade suffix";
        SearingBlow duplicate = new SearingBlow();
        duplicate.uuid = UUID.fromString("00000000-0000-4000-8000-000000000002");
        List<AbstractCard> master = Arrays.asList(upgraded, duplicate);
        MapRoomNode first = new MapRoomNode(2, 0), elite = new MapRoomNode(3, 1), last = new MapRoomNode(3, 2);
        first.room = new MonsterRoom();
        elite.room = new MonsterRoomElite();
        elite.hasEmeraldKey = true;
        last.room = new MonsterRoom();
        first.addEdge(new MapEdge(2, 0, 3, 1));
        elite.addEdge(new MapEdge(3, 1, 3, 2));
        List<List<MapRoomNode>> rows = Arrays.asList(Arrays.asList(first), Arrays.asList(elite), Arrays.asList(last));
        RandomXS128 global = (RandomXS128) MathUtils.random;
        long state0 = global.getState(0), state1 = global.getState(1);
        Gson gson = new Gson();
        String stable = gson.toJson(HeadlessStateExport.cards(master));
        for (int i = 0; i < 200; i++) {
            check(stable.equals(gson.toJson(HeadlessStateExport.cards(master))), "unstable card export");
            check(HeadlessStateExport.map(rows, first).get("nodes") instanceof List, "map export");
        }
        check(global.getState(0) == state0 && global.getState(1) == state1, "export consumed RNG");
        check(upgraded.timesUpgraded == 3 && upgraded.costForTurn == 0 && upgraded.damage == 99, "card mutated");
        check(first.getEdges().size() == 1 && elite.hasEmeraldKey && !first.taken, "map mutated");
        check("BOOT".equals(HeadlessStateExport.capture(NoRunGuard.kernel()).get("phase")), "BOOT export");
        List<Map<String, Object>> past = HeadlessStateExport.history(
                Arrays.asList("?", "M", "R"), Arrays.asList("M", "M", "R"), 3);
        check(past.size() == 2 && "?".equals(past.get(0).get("selected_symbol"))
                && "M".equals(past.get(0).get("resolved_symbol")), "resolved past history");
        check(global.getState(0) == state0 && global.getState(1) == state1, "BOOT/history export consumed RNG");
        Map<String, Object> fixture = new LinkedHashMap<>();
        fixture.put("bridgeSchema", HeadlessStateExport.SCHEMA);
        fixture.put("phase", "PLAYER_TURN"); // Synthetic observation only; engine remains BOOT.
        fixture.put("floor", 1);
        fixture.put("act", 1);
        fixture.put("ascension", 10);
        fixture.put("room", "MonsterRoom");
        fixture.put("roomPhase", "COMBAT");
        fixture.put("act_boss", "The Guardian");
        fixture.put("firstRoomChosen", true);
        Map<String, Object> player = new LinkedHashMap<>();
        player.put("className", "IRONCLAD");
        player.put("hp", 70);
        player.put("maxHp", 80);
        player.put("gold", 99);
        player.put("block", 0);
        player.put("energy", 3);
        player.put("masterDeck", HeadlessStateExport.cards(master));
        player.put("hand", HeadlessStateExport.cards(Arrays.asList(upgraded)));
        player.put("drawPile", HeadlessStateExport.cards(Arrays.asList(duplicate)));
        player.put("discardPile", new ArrayList<>());
        player.put("exhaustPile", new ArrayList<>());
        player.put("relics", new ArrayList<>());
        player.put("potions", new ArrayList<>());
        fixture.put("player", player);
        fixture.put("map", HeadlessStateExport.map(rows, first));
        fixture.put("observedKeys", HeadlessStateExport.keys(false, true, false));
        fixture.put("history", HeadlessStateExport.history(Arrays.asList("?", "M", "R"), Arrays.asList("M", "M", "R"), 1));
        Files.writeString(Path.of(args[2]), gson.toJson(fixture));
        NoRunGuard.verify();
        System.out.println("STATE_EXPORT_FIXTURE_OK reads=200 run_started=false rng_unchanged=true");
    }
}

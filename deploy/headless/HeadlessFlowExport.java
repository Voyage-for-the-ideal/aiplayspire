import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ModHelper;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.screens.GameOverScreen;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only public flow facts. No run creation, draining, or action calls. */
public final class HeadlessFlowExport {
    public static final String SCHEMA = "headless-flow-v1";
    private HeadlessFlowExport() {}

    public static List<Object> mapChoices(List<? extends List<MapRoomNode>> rows, MapRoomNode current,
            boolean firstChosen, int wingedCharges, boolean flight, int act) {
        List<Object> result = new ArrayList<>();
        if (rows == null) return result;
        Set<String> incoming = new HashSet<>();
        for (var row : rows) for (var n : row) for (var e : n.getEdges()) incoming.add(e.dstX + "," + e.dstY);
        for (var row : rows) for (var n : row) {
            if (n.room == null || (!n.hasEdges() && !incoming.contains(n.x + "," + n.y))) continue;
            boolean normal = current != null && current.isConnectedTo(n);
            boolean winged = false;
            if (firstChosen && current != null && !normal && (flight || wingedCharges > 0))
                for (var e : current.getEdges()) if (e.dstY == n.y) winged = true;
            boolean legal = firstChosen ? normal || winged : n.y == 0 && n.hasEdges();
            if (legal) result.add(HeadlessInteractionExport.object("x", n.x, "y", n.y,
                    "symbol", n.room.getMapSymbol(), "winged", winged));
        }
        if (firstChosen && current != null && current.y == 14 && act < 4)
            result.add(HeadlessInteractionExport.object("x", -1, "y", 15, "symbol", "B", "winged", false));
        return result;
    }

    public static Map<String, Object> campfireOption(String className, int index, boolean usable) {
        return HeadlessInteractionExport.object("class_name", className, "native_index", index, "usable", usable);
    }

    public static String route(String phase, String room, String dungeon, int ascension, int remaining, String event) {
        if (!(phase.equals("REWARD") || phase.equals("PROCEED"))) return "none";
        if (room.equals("TreasureRoomBoss")) return "next_act";
        if (room.equals("MonsterRoomBoss")) {
            if (dungeon.equals("TheBeyond") && ascension >= 20 && remaining == 2) return "second_boss";
            if (dungeon.equals("TheEnding")) return "true_victory";
            return dungeon.equals("TheBeyond") ? "heart_event" : "boss_chest";
        }
        if (phase.equals("REWARD") && room.equals("EventRoom") && !Set.of("Mushrooms", "MaskedBandits", "DeadAdventurer", "Lab", "Colosseum", "MysteriousSphere", "MindBloom").contains(event)) return "event_resume";
        return "map";
    }

    /** isDungeonBeaten alone is deliberately not a terminal receipt. */
    public static Map<String, Object> terminal(boolean playerDead, String room, String screen,
            boolean victory, boolean deathScreen, boolean victoryScreen, int act) {
        if (act == 4 && room.equals("TrueVictoryRoom") && screen.equals("VICTORY") && victory && victoryScreen)
            return HeadlessInteractionExport.object("outcome", "victory", "heart", true);
        if (screen.equals("DEATH") && deathScreen && playerDead) {
            if (act == 3 && room.equals("VictoryRoom") && victory)
                return HeadlessInteractionExport.object("outcome", "victory", "heart", false);
            if (!victory) return HeadlessInteractionExport.object("outcome", "defeat", "heart", false);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static void enrich(Map<String, Object> out) {
        if (AbstractDungeon.player == null) return;
        var player = AbstractDungeon.player;
        var room = AbstractDungeon.getCurrRoom();
        String roomName = room.getClass().getSimpleName(), screen = AbstractDungeon.screen.name();
        String phase = String.valueOf(out.get("phase"));
        String event = room.event == null ? "" : room.event.getClass().getSimpleName();
        Map<String, Object> menu = (Map<String, Object>) out.get("interaction");
        List<Object> path = new ArrayList<>();
        if (AbstractDungeon.pathX.size() != AbstractDungeon.pathY.size()) throw new IllegalStateException("Path axes differ");
        for (int i = 0; i < AbstractDungeon.pathX.size(); i++)
            path.add(HeadlessInteractionExport.object("x", AbstractDungeon.pathX.get(i), "y", AbstractDungeon.pathY.get(i)));
        var terminal = terminal(player.isDead, roomName, screen, GameOverScreen.isVictory,
                AbstractDungeon.deathScreen != null, AbstractDungeon.victoryScreen != null, AbstractDungeon.actNum);
        Map<String, Object> flow = HeadlessInteractionExport.object("schema", SCHEMA, "dungeon", AbstractDungeon.id,
                "flight", ModHelper.isModEnabled("Flight"), "final_act_available", Settings.isFinalActAvailable,
                "path", path, "bosses_remaining", AbstractDungeon.bossList.size(),
                "player_dead", player.isDead, "victory", GameOverScreen.isVictory,
                "result_screen", screen.equals("DEATH") && AbstractDungeon.deathScreen != null ? "DEATH"
                    : screen.equals("VICTORY") && AbstractDungeon.victoryScreen != null ? "VICTORY" : "NONE",
                "terminal", terminal, "event_class", event, "campfire_history", CardCrawlGame.metricData.campfire_choices,
                "previous_screen", AbstractDungeon.previousScreen==null?"NONE":AbstractDungeon.previousScreen.name(),
                "cancel_visible", HeadlessFlowKernel.cancelVisible(),
                "route", phase.equals("REWARD") && HeadlessFlowKernel.canReturnToShop()?"shop_return"
                    :route(phase, roomName, AbstractDungeon.id, AbstractDungeon.ascensionLevel, AbstractDungeon.bossList.size(), event));
        menu.put("flow", flow);
        if (terminal != null) out.put("phase", terminal.get("outcome").equals("victory") ? "RUN_COMPLETE" : "GAME_OVER");
        if (phase.equals("MAP")) menu.put("map_choices", mapChoices(AbstractDungeon.map, AbstractDungeon.currMapNode,
                AbstractDungeon.firstRoomChosen, player.hasRelic("WingedGreaves") ? player.getRelic("WingedGreaves").counter : 0,
                ModHelper.isModEnabled("Flight"), AbstractDungeon.actNum));
        if (phase.equals("CAMPFIRE") && room instanceof com.megacrit.cardcrawl.rooms.RestRoom) {
            var ui = ((com.megacrit.cardcrawl.rooms.RestRoom) room).campfireUI;
            List<Object> options = new ArrayList<>();
            for (var option : ui.getButtonsForHeadless()) options.add(campfireOption(option.getClass().getSimpleName(), options.size(), option.usable));
            menu.put("campfire", HeadlessInteractionExport.object("selected", ui.somethingSelected, "options", options));
        }
    }
}

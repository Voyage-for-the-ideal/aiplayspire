import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.headless.GameKernel;
import com.megacrit.cardcrawl.headless.HeadlessObservation;
import com.megacrit.cardcrawl.map.MapEdge;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Add source facts lost by upstream's compact observation. No action methods. */
public final class HeadlessStateExport {
    public static final String SCHEMA = "headless-gamestate-v1";
    private HeadlessStateExport() {}

    public static List<Map<String, Object>> cards(List<AbstractCard> source) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < source.size(); index++) {
            AbstractCard card = source.get(index);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("index", index);
            out.put("uuid", card.uuid.toString()); // Actual instance identity; never makeCopy or randomUUID.
            out.put("id", card.cardID);
            out.put("name", card.name);
            out.put("cost", card.cost);
            out.put("costForTurn", card.costForTurn);
            out.put("type", card.type.name());
            out.put("rarity", card.rarity.name());
            out.put("target", card.target.name());
            out.put("timesUpgraded", card.timesUpgraded);
            out.put("upgraded", card.upgraded);
            out.put("misc", card.misc);
            out.put("bottled", card.inBottleFlame || card.inBottleLightning || card.inBottleTornado);
            out.put("canUpgrade", card.canUpgrade());
            out.put("baseDamage", card.baseDamage);
            out.put("baseBlock", card.baseBlock);
            out.put("baseMagicNumber", card.baseMagicNumber);
            out.put("damage", card.damage);
            out.put("block", card.block);
            out.put("magicNumber", card.magicNumber);
            out.put("exhaust", card.exhaust);
            result.add(out);
        }
        return result;
    }

    public static Map<String, Object> keys(boolean ruby, boolean emerald, boolean sapphire) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ruby", ruby);
        result.put("emerald", emerald);
        result.put("sapphire", sapphire);
        return result;
    }

    public static List<Map<String, Object>> history(List<String> selected, List<String> resolved, int currentFloor) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (selected == null || resolved == null) return result;
        for (int i = 0; i < Math.min(selected.size(), resolved.size()) && i + 1 < currentFloor; i++) {
            if (resolved.get(i) == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("floor", i + 1);
            row.put("selected_symbol", selected.get(i));
            row.put("resolved_symbol", resolved.get(i));
            result.add(row);
        }
        return result;
    }

    private static String position(int x, int y) { return x + "," + y; }

    public static Map<String, Object> map(List<? extends List<MapRoomNode>> rows, MapRoomNode current) {
        if (rows == null) return null;
        Set<String> incoming = new HashSet<>();
        for (List<MapRoomNode> row : rows) for (MapRoomNode node : row) {
            if (node == null) continue;
            for (MapEdge edge : node.getEdges()) incoming.add(position(edge.dstX, edge.dstY));
        }
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (List<MapRoomNode> row : rows) for (MapRoomNode node : row) {
            if (node == null || node.room == null || (!node.hasEdges() && !incoming.contains(position(node.x, node.y)))) continue;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("x", node.x);
            out.put("y", node.y);
            out.put("symbol", node.getRoomSymbol(true));
            out.put("burning", node.hasEmeraldKey);
            // Only real engine edges. Winged candidates/route plans are not topology.
            Map<String, Map<String, Object>> unique = new TreeMap<>();
            for (MapEdge edge : node.getEdges()) {
                Map<String, Object> child = new LinkedHashMap<>();
                child.put("x", edge.dstX);
                child.put("y", edge.dstY);
                unique.put(position(edge.dstX, edge.dstY), child);
            }
            out.put("children", new ArrayList<>(unique.values()));
            nodes.add(out);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        if (current != null) {
            Map<String, Object> at = new LinkedHashMap<>();
            at.put("x", current.x);
            at.put("y", current.y);
            result.put("current", at);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> capture(GameKernel kernel) {
        Map<String, Object> out = HeadlessObservation.build(kernel);
        out.put("bridgeSchema", SCHEMA);
        AbstractPlayer player = AbstractDungeon.player;
        if (player == null) return out;
        AbstractRoom room = AbstractDungeon.currMapNode == null ? null : AbstractDungeon.getCurrRoom();
        boolean combat = room != null && room.phase == AbstractRoom.RoomPhase.COMBAT && !room.isBattleOver;
        Map<String, Object> po = new LinkedHashMap<>((Map<String, Object>) out.get("player"));
        po.put("masterDeck", cards(player.masterDeck.group));
        po.put("hand", combat ? cards(player.hand.group) : new ArrayList<>());
        po.put("drawPile", combat ? cards(player.drawPile.group) : new ArrayList<>());
        po.put("discardPile", combat ? cards(player.discardPile.group) : new ArrayList<>());
        po.put("exhaustPile", combat ? cards(player.exhaustPile.group) : new ArrayList<>());
        out.put("player", po);
        out.put("firstRoomChosen", AbstractDungeon.firstRoomChosen);
        out.put("map", map(AbstractDungeon.map, AbstractDungeon.currMapNode));
        out.put("observedKeys", keys(Settings.hasRubyKey, Settings.hasEmeraldKey, Settings.hasSapphireKey));
        out.put("history", CardCrawlGame.metricData == null ? new ArrayList<>()
                : history(CardCrawlGame.metricData.path_taken, CardCrawlGame.metricData.path_per_floor, AbstractDungeon.floorNum));
        Map<String, Object> mode = new LinkedHashMap<>();
        mode.put("is_daily", Settings.isDailyRun);
        mode.put("is_endless", Settings.isEndless);
        mode.put("is_trial", Settings.isTrial);
        mode.put("is_beta", Settings.isBeta);
        mode.put("chose_seed", Settings.seedSet);
        mode.put("special_seed", Settings.specialSeed);
        out.put("runMode", mode);
        if (AbstractDungeon.cardRewardScreen != null && "CARD_PICK".equals(out.get("phase"))) {
            out.put("cardRewardOptions", cards(AbstractDungeon.cardRewardScreen.rewardGroup));
        }
        if ("CARD_SELECT".equals(out.get("phase"))) {
            Map<String, Object> selection = new LinkedHashMap<>((Map<String, Object>) out.get("cardSelection"));
            if ("grid".equals(selection.get("kind"))) {
                selection.put("options", cards(AbstractDungeon.gridSelectScreen.targetGroup.group));
                selection.put("selected", cards(AbstractDungeon.gridSelectScreen.selectedCards));
            } else if ("hand".equals(selection.get("kind"))) {
                selection.put("options", cards(player.hand.group));
                selection.put("selected", cards(AbstractDungeon.handCardSelectScreen.selectedCards.group));
            }
            out.put("cardSelection", selection);
        }
        return out;
    }
}

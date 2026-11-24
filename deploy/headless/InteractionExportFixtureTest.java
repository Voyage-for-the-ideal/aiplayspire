import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.megacrit.cardcrawl.cards.red.SearingBlow;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.neow.NeowReward;
import com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/** Source-extractor and wire fixtures, never a mounted player or active event. */
public final class InteractionExportFixtureTest {
    private enum Screen { INTRO, RESULT }
    private static final class BigFish extends AbstractEvent {
        private Screen screen = Screen.INTRO;
        private int healAmt = 10;
        @Override protected void buttonEffect(int button) { throw new AssertionError("Offline fixture has no event actions"); }
    }
    private static final class KnowingSkull extends AbstractEvent {
        private String screen = "ASK";
        private int potionCost = 6, goldCost = 6, cardCost = 7, leaveCost = 6;
        @Override protected void buttonEffect(int button) { throw new AssertionError("Offline fixture has no event actions"); }
    }

    private static JsonObject snapshot(JsonObject base, String phase) {
        JsonObject result = base.deepCopy();
        result.addProperty("phase", phase);
        result.addProperty("roomPhase", "COMPLETE");
        return result;
    }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[2]);
        Path basePath = output.resolveSibling("base-state-fixture.json");
        StateExportFixtureTest.main(new String[] {args[0], args[1], basePath.toString()});
        NoRunGuard.verify();
        Gson json = new Gson();
        JsonObject base = JsonParser.parseString(Files.readString(basePath)).getAsJsonObject();
        BigFish localEvent = new BigFish();
        ArrayList<LargeDialogOptionButton> buttons = new ArrayList<>();
        buttons.add(new LargeDialogOptionButton(0, "localized heal"));
        buttons.add(new LargeDialogOptionButton(1, "disabled", true));
        buttons.add(new LargeDialogOptionButton(2, "localized reward"));
        SearingBlow item = new SearingBlow();
        item.uuid = UUID.fromString("00000000-0000-4000-8000-000000000099");
        item.price = 20;
        RandomXS128 rng = (RandomXS128) MathUtils.random;
        long a = rng.getState(0), b = rng.getState(1);
        ArrayList<LargeDialogOptionButton> skullButtons = new ArrayList<>();
        for (String label : new String[] {"potion", "gold", "card", "leave"})
            skullButtons.add(new LargeDialogOptionButton(skullButtons.size(), label));
        JsonObject skullCard = json.toJsonTree(HeadlessInteractionExport.eventView(new KnowingSkull(), skullButtons))
                .getAsJsonObject().getAsJsonArray("choices").get(2).getAsJsonObject();
        JsonObject skull = skullCard.getAsJsonArray("outcomes").get(0).getAsJsonObject();
        var skullEffects = skull.getAsJsonArray("effects");
        if (!skullCard.get("followup").getAsString().equals("EVENT")
                || skull.get("probability").getAsDouble()!=1.0 || skullEffects.size()!=2
                || !skullEffects.get(0).getAsJsonObject().get("type").getAsString().equals("lose_hp")
                || skullEffects.get(0).getAsJsonObject().get("amount").getAsInt()!=7
                || !skullEffects.get(1).getAsJsonObject().get("type").getAsString().equals("random_card")
                || !skullEffects.get(1).getAsJsonObject().get("pool").getAsString().equals("COLORLESS_UNCOMMON")
                || skullEffects.get(1).getAsJsonObject().has("card_id"))
            throw new AssertionError("Knowing Skull direct card semantics");
        var golden = HeadlessInteractionExport.goldenWingGoldOutcomes(50, 80);
        if (golden.size() != 31) throw new AssertionError("GoldenWing range length");
        double probability = 0;
        for (int i = 0; i < golden.size(); i++) {
            JsonObject outcome = json.toJsonTree(golden.get(i)).getAsJsonObject();
            probability += outcome.get("probability").getAsDouble();
            JsonObject effect = outcome.getAsJsonArray("effects").get(0).getAsJsonObject();
            if (!effect.get("type").getAsString().equals("gain_gold") || effect.get("amount").getAsInt() != 50 + i)
                throw new AssertionError("GoldenWing inclusive gold outcome");
        }
        if (Math.abs(probability - 1) > 1e-10) throw new AssertionError("GoldenWing probabilities");
        String stable = json.toJson(HeadlessInteractionExport.eventView(localEvent, buttons));
        for (int i = 0; i < 200; i++) {
            if (!stable.equals(json.toJson(HeadlessInteractionExport.eventView(localEvent, buttons)))) throw new AssertionError("event export changed");
        }
        int neowCases = 0;
        for (NeowReward.NeowRewardType type : NeowReward.NeowRewardType.values()) {
            for (NeowReward.NeowRewardDrawback drawback : NeowReward.NeowRewardDrawback.values()) {
                json.toJson(HeadlessInteractionExport.neowChoice(type.name(), drawback.name(), 8, 70, "Burning Blood"));
                neowCases++;
            }
        }
        JsonObject before = snapshot(base, "EVENT");
        before.addProperty("room", "EventRoom");
        before.addProperty("roomPhase", "EVENT");
        Map<String, Object> beforeMenu = HeadlessInteractionExport.object("schema", HeadlessInteractionExport.SCHEMA,
                "event", HeadlessInteractionExport.eventView(localEvent, buttons),
                "event_transitions", HeadlessInteractionExport.object("0", HeadlessInteractionExport.object("phases", HeadlessInteractionExport.list("RESULT"))));
        before.add("interaction", json.toJsonTree(beforeMenu));
        localEvent.screen = Screen.RESULT; // Change only the isolated fixture object, not engine state.
        ArrayList<LargeDialogOptionButton> done = new ArrayList<>();
        done.add(new LargeDialogOptionButton(0, "leave"));
        JsonObject after = snapshot(before, "EVENT");
        after.getAsJsonObject("player").addProperty("hp", 80);
        after.add("interaction", json.toJsonTree(HeadlessInteractionExport.object("schema", HeadlessInteractionExport.SCHEMA,
                "event", HeadlessInteractionExport.eventView(localEvent, done))));
        JsonObject shop = snapshot(base, "SHOP");
        shop.addProperty("room", "ShopRoom");
        shop.add("interaction", json.toJsonTree(HeadlessInteractionExport.object("schema", HeadlessInteractionExport.SCHEMA,
                "shop", HeadlessInteractionExport.object("purge_available", true, "purge_cost", 75,
                        "purge_cards", base.getAsJsonObject("player").getAsJsonArray("masterDeck"),
                        "items", HeadlessInteractionExport.list(HeadlessInteractionExport.shopCard(item, "card:" + item.uuid, 3, true))))));
        JsonObject reward = snapshot(base, "REWARD");
        reward.add("interaction", json.toJsonTree(HeadlessInteractionExport.object("schema", HeadlessInteractionExport.SCHEMA,
                "can_proceed", true, "rewards", HeadlessInteractionExport.list(HeadlessInteractionExport.object("native_index", 0, "type", "GOLD", "amount", 25)))));
        JsonObject grid = snapshot(base, "CARD_SELECT");
        grid.add("cardSelection", json.toJsonTree(HeadlessInteractionExport.object("kind", "grid", "options",
                base.getAsJsonObject("player").getAsJsonArray("masterDeck"), "selected", new ArrayList<>())));
        grid.add("interaction", json.toJsonTree(HeadlessInteractionExport.object("schema", HeadlessInteractionExport.SCHEMA,
                "selection", HeadlessInteractionExport.object("kind", "grid", "purpose", "upgrade", "count", 1,
                        "locked", false, "can_confirm", false, "any_number", false, "can_pick_zero", false, "auto_confirm", false))));
        Map<String, Object> result = HeadlessInteractionExport.object("event_before", before, "event_after", after,
                "golden_wing_gold_outcomes", golden,
                "shop", shop, "reward", reward, "grid", grid,
                "neow_reward", HeadlessInteractionExport.neowChoice("TEN_PERCENT_HP_BONUS", "NONE", 8, 70, "Burning Blood"));
        Files.writeString(output, json.toJson(result));
        if (!"BOOT".equals(HeadlessInteractionExport.capture(NoRunGuard.kernel()).get("phase")))
            throw new AssertionError("Actual engine left BOOT");
        if (rng.getState(0) != a || rng.getState(1) != b) throw new AssertionError("Semantic export consumed RNG");
        NoRunGuard.verify();
        System.out.println("INTERACTION_EXPORT_FIXTURE_OK reads=200 neow_cases=" + neowCases + " run_started=false event_actions_executed=0 rng_unchanged=true");
    }
}

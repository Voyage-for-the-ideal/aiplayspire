package battleaimod.strategy;

import battleaimod.evaluation.*;
import com.google.gson.*;
import com.megacrit.cardcrawl.cards.AbstractCard.CardColor;
import com.megacrit.cardcrawl.cards.AbstractCard.CardType;
import com.megacrit.cardcrawl.cards.blue.*;
import com.megacrit.cardcrawl.cards.green.*;
import com.megacrit.cardcrawl.cards.purple.*;
import com.megacrit.cardcrawl.cards.red.*;
import ludicrousspeed.simulator.commands.*;
import org.junit.Test;
import savestate.SaveState;
import savestate.StateFactories;

import java.util.*;

import static org.junit.Assert.*;

/** Outcome/ranking regressions on snapshots; no graphics or live game required. */
public class CharacterStrategiesTest {
    private JsonObject state(String role, int block) {
        JsonObject state = TestStateBuilder.saveState(60, 70, block, TestStateBuilder.attacking(60, 20));
        state.getAsJsonObject("player_state").addProperty("chosen_class_name", role);
        return state;
    }
    private void power(JsonObject state, String id, int amount) {
        JsonObject power = new JsonObject();
        power.addProperty("power_id", id); power.addProperty("amount", amount);
        power.addProperty("just_applied", false);
        state.getAsJsonObject("player_state").getAsJsonObject("creature").getAsJsonArray("powers").add(power);
    }
    private JsonObject enemy(JsonObject state) {
        return state.getAsJsonObject("cur_map_node_state").getAsJsonArray("monster_data").get(0).getAsJsonObject();
    }
    private void enemyPower(JsonObject state, String id, int amount) {
        JsonObject power = new JsonObject();
        power.addProperty("power_id", id); power.addProperty("amount", amount);
        power.addProperty("just_applied", false);
        enemy(state).getAsJsonObject("creature").getAsJsonArray("powers").add(power);
    }
    private StrategyContext context(JsonObject state) {
        SaveState snapshot = new SaveState(state);
        return new StrategyContext(snapshot, CombatFeatures.extract(snapshot, snapshot, 60));
    }
    private EvaluationResult evaluate(JsonObject state) {
        SaveState snapshot = new SaveState(state);
        return TacticalEvaluator.evaluate(snapshot, snapshot, 60);
    }
    private StrategyContext.Card card(String id, int cost, int damage, int block, CardColor color) {
        return new StrategyContext.Card(id, cost, damage, block, 0, false, false, false, false,
                damage > 0 ? CardType.ATTACK : CardType.SKILL, color);
    }
    private void hand(JsonObject state, String id, int cost, int damage, int block) {
        if (!StateFactories.cardIdToIndexMap.containsKey(id)) {
            List<String> ids = new ArrayList<>();
            if (StateFactories.cardIds != null) ids.addAll(Arrays.asList(StateFactories.cardIds));
            StateFactories.cardIdToIndexMap.put(id, ids.size()); ids.add(id);
            StateFactories.cardIds = ids.toArray(new String[0]);
        }
        JsonObject card = new JsonObject();
        card.addProperty("card_id", id);
        card.addProperty("uuid", new UUID(0, StateFactories.cardIdToIndexMap.get(id) + 1).toString());
        for (String field : Arrays.asList("upgraded", "in_bottle_lightning", "in_bottle_tornado", "in_bottle_flame",
                "free_to_play_once", "is_cost_modified_for_turn", "is_cost_modified", "exhaust", "purge_on_use",
                "is_ethereal", "retain", "self_retain", "shuffle_back_into_draw_pile")) card.addProperty(field, false);
        for (String field : Arrays.asList("magic_number", "base_magic_number", "times_upgraded", "misc")) card.addProperty(field, 0);
        card.addProperty("cost", cost); card.addProperty("cost_for_turn", cost);
        card.addProperty("damage", damage); card.addProperty("base_damage", damage);
        card.addProperty("block", block); card.addProperty("base_block", block);
        card.add("modifiers", JsonNull.INSTANCE);
        state.getAsJsonObject("player_state").getAsJsonArray("hand").add(card);
    }
    private void orb(JsonObject state, String kind, int passive, int evoke) {
        JsonObject orb = new JsonObject();
        orb.addProperty("lookup_key", kind); orb.addProperty("passive_amount", passive);
        orb.addProperty("evoke_amount", evoke); orb.addProperty("base_passive_amount", passive);
        orb.addProperty("base_evoke_amount", evoke);
        state.getAsJsonObject("player_state").getAsJsonArray("orbs").add(orb);
        state.getAsJsonObject("player_state").addProperty("max_orbs", 3);
    }

    @Test public void wrathDoublesIncomingAndIntangibleCapsEachHitAfterModifiers() {
        JsonObject state = state("WATCHER", 0);
        state.getAsJsonObject("player_state").addProperty("stance", "Wrath");
        power(state, "Vulnerable", 1); enemyPower(state, "Weakened", 1);
        assertEquals(45, context(state).features.currentIncomingDamage);
        enemy(state).getAsJsonObject("move_info").addProperty("is_multi_damage", true);
        enemy(state).getAsJsonObject("move_info").addProperty("multiplier", 3);
        power(state, "IntangiblePlayer", 1);
        assertEquals(3, context(state).features.currentIncomingDamage);
    }
    @Test public void blockIsSpentBeforeBufferAndBlockedHitsPreserveStacks() {
        JsonObject state = state("DEFECT", 20);
        power(state, "Buffer", 1);
        enemy(state).getAsJsonObject("move_info").addProperty("is_multi_damage", true);
        enemy(state).getAsJsonObject("move_info").addProperty("multiplier", 3);
        StrategyContext c = context(state);
        assertEquals(20, c.features.currentIncomingDamage);
        assertEquals(20 * TacticalEvaluator.IMMEDIATE_THREAT_WEIGHT, c.features.aliveThreat);
        assertEquals(c.features.aliveThreat, ThreatEvaluator.totalThreat(c.state));
    }
    @Test public void poisonPotentialIsCappedAndDoesNotPretendTheEnemyAlreadyDied() {
        JsonObject state = state("THE_SILENT", 0);
        enemyPower(state, "Poison", 999);
        EvaluationResult value = evaluate(state);
        assertEquals(600, value.mechanismScore);
        assertEquals(0, value.lethalScore);
        assertTrue(value.threatScore < 0);
    }
    @Test public void secondPoisonTickIsDiscountedAndDeadTargetsHaveNoPotential() {
        JsonObject state = state("THE_SILENT", 0);
        enemyPower(state, "Poison", 5);
        assertEquals(70, evaluate(state).mechanismScore);
        enemy(state).getAsJsonObject("creature").addProperty("current_health", 0);
        assertEquals(0, evaluate(state).mechanismScore);
    }
    @Test public void physicalDefectAndOtherClassesHaveNoZeroSlotPenalty() {
        JsonObject defect = state("DEFECT", 0), ironclad = state("IRONCLAD", 0);
        assertEquals(evaluate(ironclad).totalScore, evaluate(defect).totalScore);
        assertEquals(0, evaluate(defect).mechanismScore);
    }
    @Test public void focusIsNotCountedTwiceAndNegativeFocusUsesActualOrbOutput() {
        JsonObject state = state("DEFECT", 0);
        orb(state, "Lightning", 0, 0); power(state, "Focus", -9);
        assertEquals(0, evaluate(state).mechanismScore);
        assertEquals(0, evaluate(state).scalingScore);
        JsonObject focused = state("DEFECT", 0);
        orb(focused, "Lightning", 5, 10); power(focused, "Focus", 2);
        assertEquals(75, evaluate(focused).mechanismScore);
        assertEquals(0, evaluate(focused).scalingScore);
    }
    @Test public void darkStoredDamageRequiresAnAccessibleFrontOrb() {
        JsonObject front = state("DEFECT", 0);
        hand(front, Dualcast.ID, 1, 0, 0); orb(front, "Dark", 6, 40);
        assertEquals(200, evaluate(front).mechanismScore);
        JsonObject back = state("DEFECT", 0);
        hand(back, Dualcast.ID, 1, 0, 0); orb(back, "EmptyOrbSlot", 0, 0); orb(back, "Dark", 6, 40);
        assertEquals(0, evaluate(back).mechanismScore);
    }
    @Test public void strengthNeedsAttackFuelAndExhaustEngineNeedsCards() {
        JsonObject empty = state("IRONCLAD", 0); power(empty, "Strength", 5);
        assertEquals(0, evaluate(empty).mechanismScore);
        hand(empty, Strike_Red.ID, 1, 6, 0);
        assertEquals(50, evaluate(empty).mechanismScore);
        JsonObject engine = state("IRONCLAD", 0);
        power(engine, "Feel No Pain", 3); power(engine, "Dark Embrace", 1); power(engine, "Corruption", -1);
        hand(engine, Defend_Red.ID, 1, 0, 5);
        assertTrue(evaluate(engine).mechanismScore > evaluate(empty).mechanismScore);
    }
    @Test public void temporaryBlockOnlyHasFutureValueWhenRetained() {
        JsonObject state = state("IRONCLAD", 25);
        assertEquals(0, evaluate(state).mechanismScore);
        power(state, "Barricade", -1);
        assertEquals(250, evaluate(state).mechanismScore);
    }
    @Test public void wraithFormCostLowersValueAndTemporaryBuffsDoNotRankWinningLines() {
        JsonObject state = state("THE_SILENT", 0); power(state, "Wraith Form v2", -1);
        assertEquals(-20, evaluate(state).mechanismScore);
        enemy(state).getAsJsonObject("creature").addProperty("current_health", 0);
        int won = evaluate(state).totalScore;
        power(state, "Strength", 999); power(state, "Focus", 999);
        assertEquals(won, evaluate(state).totalScore);
    }
    @Test public void mutualDeathCannotReceiveVictoryBonus() {
        JsonObject state = state("WATCHER", 0);
        state.getAsJsonObject("player_state").getAsJsonObject("creature").addProperty("current_health", 0);
        enemy(state).getAsJsonObject("creature").addProperty("current_health", 0);
        EvaluationResult result = evaluate(state);
        assertEquals(TacticalEvaluator.DEATH_PENALTY, result.totalScore);
        assertEquals(0, result.lethalScore);
    }
    @Test public void poisonComesBeforeCatalystAndCatalystNeedsPoison() {
        StrategyContext c = context(state("THE_SILENT", 0));
        StrategyContext.Card catalyst = card(Catalyst.ID, 1, 0, 0, CardColor.GREEN);
        assertTrue(CommandOrdering.playPriority(c, catalyst, c.state.curMapNodeState.monsterData.get(0)) < 0);
        JsonObject state = state("THE_SILENT", 0); hand(state, Catalyst.ID, 1, 0, 0);
        c = context(state);
        assertTrue(CommandOrdering.playPriority(c, card(DeadlyPoison.ID, 1, 0, 0, CardColor.GREEN), null)
                > CommandOrdering.playPriority(c, catalyst, null));
    }
    @Test public void crossColorCardsUseTheirActualMechanics() {
        StrategyContext c = context(state("IRONCLAD", 0));
        assertTrue(CommandOrdering.playPriority(c, card(AfterImage.ID, 1, 0, 0, CardColor.GREEN), null) > 0);
        assertTrue(CommandOrdering.playPriority(c, card(Catalyst.ID, 1, 0, 0, CardColor.GREEN), null) < 0);
    }
    @Test public void wrathEntryRequiresAnExitOrSafetyAndRepeatedEntryIsDelayed() {
        JsonObject state = state("WATCHER", 0);
        StrategyContext.Card eruption = card(Eruption.ID, 2, 9, 0, CardColor.PURPLE);
        assertTrue(CommandOrdering.playPriority(context(state), eruption, null) < 0);
        hand(state, EmptyFist.ID, 1, 9, 0);
        assertTrue(CommandOrdering.playPriority(context(state), eruption, null) > 0);
        state.getAsJsonObject("player_state").addProperty("stance", "Wrath");
        assertTrue(CommandOrdering.playPriority(context(state), eruption, null) < 0);
    }
    @Test public void uselessTurboAndEmptyDualcastAreDelayed() {
        StrategyContext c = context(state("DEFECT", 0));
        assertTrue(CommandOrdering.playPriority(c, card(Turbo.ID, 0, 0, 0, CardColor.BLUE), null) < 0);
        assertTrue(CommandOrdering.playPriority(c, card(Dualcast.ID, 1, 0, 0, CardColor.BLUE), null) < 0);
    }
    @Test public void discardAndExhaustHaveDifferentTriggerPreferences() {
        StrategyContext c = context(state("THE_SILENT", 0));
        StrategyContext.Card reflex = card(Reflex.ID, -2, 0, 0, CardColor.GREEN);
        StrategyContext.Card sentinel = card(Sentinel.ID, 1, 0, 5, CardColor.RED);
        assertTrue(CommandOrdering.selectionPriority(c, reflex, CommandOrdering.Selection.DISCARD)
                > CommandOrdering.selectionPriority(c, sentinel, CommandOrdering.Selection.DISCARD));
        assertTrue(CommandOrdering.selectionPriority(c, sentinel, CommandOrdering.Selection.EXHAUST)
                > CommandOrdering.selectionPriority(c, reflex, CommandOrdering.Selection.EXHAUST));
    }
    @Test public void exhaustProtectsTheOnlyWrathExit() {
        JsonObject state = state("WATCHER", 0); state.getAsJsonObject("player_state").addProperty("stance", "Wrath");
        StrategyContext c = context(state);
        assertTrue(CommandOrdering.selectionPriority(c, card(EmptyFist.ID, 1, 9, 0, CardColor.PURPLE), CommandOrdering.Selection.EXHAUST)
                < CommandOrdering.selectionPriority(c, card(Strike_Purple.ID, 1, 6, 0, CardColor.PURPLE), CommandOrdering.Selection.EXHAUST));
    }
    @Test public void seekPrefersAffordableCardsAndRetainAvoidsEtherealCards() {
        StrategyContext c = context(state("DEFECT", 0));
        assertTrue(CommandOrdering.selectionPriority(c, card("affordable", 1, 10, 0, CardColor.BLUE), CommandOrdering.Selection.SEEK)
                > CommandOrdering.selectionPriority(c, card("expensive", 5, 10, 0, CardColor.BLUE), CommandOrdering.Selection.SEEK));
        StrategyContext.Card ethereal = new StrategyContext.Card("ethereal", 1, 30, 0, 0, false, false, true, false, CardType.ATTACK, CardColor.PURPLE);
        assertTrue(CommandOrdering.selectionPriority(c, ethereal, CommandOrdering.Selection.RETAIN) < 0);
    }
    @Test public void scryDiscardsBadCardsThenConfirmsBeforeDiscardingUsefulCards() {
        StrategyContext c = context(state("WATCHER", 0));
        List<StrategyContext.Card> cards = Arrays.asList(card("Burn", -2, 0, 0, CardColor.COLORLESS),
                card(Strike_Purple.ID, 1, 6, 0, CardColor.PURPLE));
        List<Command> commands = Arrays.asList(new GridSelectCommand(1), GridSelectConfirmCommand.INSTANCE, new GridSelectCommand(0));
        List<Command> ordered = CommandOrdering.order(c, commands, Collections.emptyList(), cards, CommandOrdering.Selection.SCRY);
        assertSame(commands.get(2), ordered.get(0)); assertSame(commands.get(1), ordered.get(1));
        assertSame(commands.get(0), ordered.get(2));
        List<Command> after = Arrays.asList(new GridSelectCommand(1), GridSelectConfirmCommand.INSTANCE);
        assertSame(GridSelectConfirmCommand.INSTANCE, CommandOrdering.order(c, after, Collections.emptyList(), cards, CommandOrdering.Selection.SCRY).get(0));
    }
    @Test public void sortingPreservesAllCommandsTargetsAndStableTies() {
        StrategyContext c = context(state("DEFECT", 0));
        List<StrategyContext.Card> cards = Arrays.asList(card("a", 1, 6, 0, CardColor.BLUE), card("b", 1, 6, 0, CardColor.BLUE));
        List<Command> original = Arrays.asList(new CardCommand(1, 0, "b"), new CardCommand(0, 0, "a"), new PotionCommand(0), new EndCommand());
        List<Command> ordered = CommandOrdering.order(c, original, cards, Collections.emptyList(), CommandOrdering.Selection.NONE);
        assertEquals(original, ordered);
        for (int i = 0; i < 30; i++) {
            List<Command> shuffled = new ArrayList<>(original); Collections.shuffle(shuffled, new Random(i));
            List<Command> result = CommandOrdering.order(c, shuffled, cards, Collections.emptyList(), CommandOrdering.Selection.NONE);
            assertEquals(new HashSet<>(original), new HashSet<>(result));
            assertEquals(result, CommandOrdering.order(c, result, cards, Collections.emptyList(), CommandOrdering.Selection.NONE));
        }
    }
    @Test public void unknownSelectionsKeepTheirOriginalOrder() {
        assertEquals(CommandOrdering.Selection.NONE, CommandOrdering.selection("ModdedSelectionAction"));
        StrategyContext c = context(state("WATCHER", 0));
        List<Command> commands = Arrays.asList(new GridSelectCommand(3), GridSelectConfirmCommand.INSTANCE, new GridSelectCommand(0));
        assertEquals(commands, CommandOrdering.order(c, commands, Collections.emptyList(), Collections.emptyList(), CommandOrdering.Selection.NONE));
    }
    @Test public void readOnlyAccessorsAndSelectionCommandEncodingRemainCompatible() {
        JsonObject state = state("WATCHER", 0); hand(state, EmptyFist.ID, 1, 9, 0);
        SaveState snapshot = new SaveState(state);
        assertEquals("Neutral", snapshot.playerState.getStanceId());
        assertEquals(1, snapshot.playerState.hand[0].getCostForTurn());
        assertEquals(9, snapshot.playerState.hand[0].getDamage());
        assertEquals(4, new HandSelectCommand(new HandSelectCommand(4).encode()).getCardIndex());
        assertEquals(7, new GridSelectCommand(new GridSelectCommand(7).encode()).getCardIndex());
    }

    @Test public void wrathEntryMustLeaveEnoughEnergyForItsExit() {
        JsonObject state = state("WATCHER", 0); hand(state, Vigilance.ID, 2, 0, 8);
        assertTrue(CommandOrdering.playPriority(context(state), card(Eruption.ID, 2, 9, 0, CardColor.PURPLE), null) < 0);
    }
    @Test public void bladeDanceEngineAndMantraHavePositiveUnsettledValue() {
        JsonObject silent = state("THE_SILENT", 0); hand(silent, BladeDance.ID, 1, 0, 0); power(silent, "Accuracy", 4);
        assertTrue(evaluate(silent).mechanismScore > 0);
        JsonObject watcher = state("WATCHER", 0); power(watcher, "Mantra", 8);
        assertTrue(evaluate(watcher).mechanismScore > 0);
    }
    @Test public void exhaustPowersWithoutAnExhaustOutletEarnNoEngineBonus() {
        JsonObject state = state("IRONCLAD", 0); power(state, "Feel No Pain", 3); power(state, "Dark Embrace", 1);
        hand(state, Defend_Red.ID, 1, 0, 5);
        assertEquals(0, evaluate(state).mechanismScore);
    }
    @Test public void bodySlamWaitsForBlockAndEntrenchRequiresExistingBlock() {
        JsonObject state = state("IRONCLAD", 0); hand(state, BodySlam.ID, 1, 0, 0);
        StrategyContext c = context(state);
        assertTrue(CommandOrdering.playPriority(c, card(Defend_Red.ID, 1, 0, 5, CardColor.RED), null)
                > CommandOrdering.playPriority(c, card(BodySlam.ID, 1, 0, 0, CardColor.RED), null));
        assertTrue(CommandOrdering.playPriority(c, card(Entrench.ID, 2, 0, 0, CardColor.RED), null) < 0);
    }
    @Test public void sortingRecomputesAfterDiscardUnlocksSneakyStrike() {
        JsonObject state = state("THE_SILENT", 0);
        StrategyContext.Card card = card(SneakyStrike.ID, 2, 12, 0, CardColor.GREEN);
        int before = CommandOrdering.playPriority(context(state), card, null);
        state.addProperty("total_discarded_this_turn", 1);
        assertTrue(CommandOrdering.playPriority(context(state), card, null) > before);
    }
    @Test public void mechanismPotentialIsBoundedWithoutChangingTerminalRules() {
        MechanismScore score = new MechanismScore(); score.add("huge", Integer.MAX_VALUE);
        assertEquals(MechanismScore.LIMIT, score.total());
        score.add("negative", -Integer.MAX_VALUE); assertEquals(0, score.total());
    }
    @Test public void calmNeedsAnActualExitAndWrathExitsBeforeLethalIncoming() {
        JsonObject calm = state("WATCHER", 0); calm.getAsJsonObject("player_state").addProperty("stance", "Calm");
        hand(calm, Vigilance.ID, 2, 0, 8);
        assertEquals(0, evaluate(calm).mechanismScore);
        hand(calm, EmptyMind.ID, 1, 0, 0);
        assertTrue(evaluate(calm).mechanismScore > 0);
        JsonObject wrath = state("WATCHER", 0); wrath.getAsJsonObject("player_state").addProperty("stance", "Wrath");
        wrath.getAsJsonObject("player_state").getAsJsonObject("creature").addProperty("current_health", 20);
        assertTrue(CommandOrdering.playPriority(context(wrath), card(EmptyFist.ID, 1, 9, 0, CardColor.PURPLE), null) > 0);
    }
    @Test public void endTurnRemainsAheadOfWastefulCatalystOrTurbo() {
        StrategyContext c = context(state("THE_SILENT", 0));
        List<StrategyContext.Card> cards = Collections.singletonList(card(Catalyst.ID, 1, 0, 0, CardColor.GREEN));
        EndCommand end = new EndCommand();
        List<Command> commands = Arrays.asList(new CardCommand(0, 0, Catalyst.ID), end);
        assertSame(end, CommandOrdering.order(c, commands, cards, Collections.emptyList(), CommandOrdering.Selection.NONE).get(0));
    }
}

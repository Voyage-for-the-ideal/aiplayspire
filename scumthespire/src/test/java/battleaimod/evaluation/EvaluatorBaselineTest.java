package battleaimod.evaluation;

import battleaimod.ValueFunctions;
import org.junit.Test;
import savestate.SaveState;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Baseline record for Phase 1: runs the legacy linear evaluator and the new
 * tactical evaluator over the same states and prints both, so later phases can
 * answer "did behavior improve because of the evaluator, or just more nodes?"
 * <p>
 * This is the minimal benchmark entry point; full battle-level benchmarks
 * (victory / ending HP / expanded nodes) run in the game client and are
 * compared in Phase 5.
 */
public class EvaluatorBaselineTest {

    @Test
    public void recordBaselineForKillVsRawDamage() {
        TestStateBuilder.Monster enemyA = TestStateBuilder.attacking(8, 18);
        TestStateBuilder.Monster enemyB = TestStateBuilder.attacking(40, 6);
        SaveState start = TestStateBuilder.state(70, 70, enemyA, enemyB);

        List<TestStateBuilder.Monster> killMonsters = TestStateBuilder.copy(
                Arrays.asList(enemyA, enemyB));
        killMonsters.get(0).hp = 0;
        SaveState killState = TestStateBuilder.state(70, 70, killMonsters);

        List<TestStateBuilder.Monster> damageMonsters = TestStateBuilder.copy(
                Arrays.asList(enemyA, enemyB));
        damageMonsters.get(1).hp = 24;
        SaveState damageState = TestStateBuilder.state(70, 70, damageMonsters);

        int legacyKill = ValueFunctions.calculateLegacyTurnScore(start, killState, 70);
        int legacyDamage = ValueFunctions.calculateLegacyTurnScore(start, damageState, 70);
        EvaluationResult newKill = TacticalEvaluator.evaluate(start, killState, 70);
        EvaluationResult newDamage = TacticalEvaluator.evaluate(start, damageState, 70);

        // Baseline record: legacy prefers raw damage (progress-heavy, threat-blind)
        assertTrue("legacy baseline: more total damage should score higher",
                legacyDamage > legacyKill);
        // New evaluator flips the preference to the kill
        assertTrue("tactical evaluator: kill must beat raw damage",
                newKill.totalScore > newDamage.totalScore);

        System.out.println("BASELINE kill-vs-raw-damage:"
                + " legacy[kill=" + legacyKill + ", damage=" + legacyDamage + "]"
                + " new[kill=" + newKill.totalScore + " (" + newKill + ")"
                + ", damage=" + newDamage.totalScore + " (" + newDamage + ")]");
    }

    @Test
    public void recordBaselineForHpRisk() {
        TestStateBuilder.Monster passive = TestStateBuilder.monster(40);
        SaveState start = TestStateBuilder.state(70, 70, passive);

        int legacyHealthyLoss = ValueFunctions.calculateLegacyTurnScore(start,
                TestStateBuilder.state(30, 70, passive), 70)
                - ValueFunctions.calculateLegacyTurnScore(start,
                TestStateBuilder.state(26, 70, passive), 70);
        int legacyNearDeathLoss = ValueFunctions.calculateLegacyTurnScore(start,
                TestStateBuilder.state(5, 70, passive), 70)
                - ValueFunctions.calculateLegacyTurnScore(start,
                TestStateBuilder.state(1, 70, passive), 70);

        EvaluationResult new30 = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(30, 70, passive), 70);
        EvaluationResult new26 = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(26, 70, passive), 70);
        EvaluationResult new5 = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(5, 70, passive), 70);
        EvaluationResult new1 = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(1, 70, passive), 70);

        int newHealthyLoss = new30.totalScore - new26.totalScore;
        int newNearDeathLoss = new5.totalScore - new1.totalScore;

        // Baseline record: legacy is linear, the 4-HP loss costs the same both times
        assertTrue("legacy baseline: HP loss is linear", legacyHealthyLoss == legacyNearDeathLoss);
        assertTrue("tactical evaluator: near-death loss must be steeper",
                newNearDeathLoss > newHealthyLoss);

        System.out.println("BASELINE hp-risk (4 HP loss):"
                + " legacy[healthy=" + legacyHealthyLoss + ", nearDeath=" + legacyNearDeathLoss + "]"
                + " new[healthy=" + newHealthyLoss + ", nearDeath=" + newNearDeathLoss + "]");
    }

    @Test
    public void recordBaselineForBattleEndBreakpoint() {
        TestStateBuilder.Monster enemy = TestStateBuilder.attacking(8, 6);
        SaveState start = TestStateBuilder.state(70, 70, enemy);

        SaveState deadState = TestStateBuilder.state(70, 70, TestStateBuilder.monster(0));
        SaveState aliveState = TestStateBuilder.state(70, 70, TestStateBuilder.attacking(1, 6));

        int legacyGap = ValueFunctions.calculateLegacyTurnScore(start, deadState, 70)
                - ValueFunctions.calculateLegacyTurnScore(start, aliveState, 70);
        int newGap = TacticalEvaluator.evaluate(start, deadState, 70).totalScore
                - TacticalEvaluator.evaluate(start, aliveState, 70).totalScore;

        // Baseline record: legacy values 1 HP of monster health linearly
        assertTrue("legacy baseline: battle end is only worth 1 HP of progress",
                legacyGap <= 10);
        assertTrue("tactical evaluator: battle end is a discrete breakpoint",
                newGap > 500_000);

        System.out.println("BASELINE battle-end (0 HP vs 1 HP):"
                + " legacy gap=" + legacyGap + ", new gap=" + newGap);
    }
}

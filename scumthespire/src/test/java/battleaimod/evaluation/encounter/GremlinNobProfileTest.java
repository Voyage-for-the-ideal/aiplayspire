package battleaimod.evaluation.encounter;

import battleaimod.evaluation.EvaluationResult;
import battleaimod.evaluation.TacticalEvaluator;
import battleaimod.evaluation.TestStateBuilder;
import org.junit.Test;
import savestate.SaveState;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Gremlin Nob: time is expensive (turn urgency), Strength consequences belong
 * to the generic threat evaluator, and death still dominates everything.
 */
public class GremlinNobProfileTest {

    private static TestStateBuilder.Monster nob(int hp, int strength) {
        TestStateBuilder.Monster m = TestStateBuilder.attacking(hp, 14);
        m.id = "GremlinNob";
        m.strength = strength;
        return m;
    }

    // ------------------------------------------------------------------
    // Test A - delay is bad
    // ------------------------------------------------------------------

    @Test
    public void laterTurnsWithNobAliveScoreLower() {
        TestStateBuilder.Monster nob = nob(80, 0);
        SaveState start = TestStateBuilder.state(70, 70, nob);

        // Identical states (same Nob HP, player HP, threat), only the turn differs
        EvaluationResult early = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(70, 70, 0, 2, nob), 70);
        EvaluationResult delayed = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(70, 70, 0, 5, nob), 70);

        assertTrue("earlier turn must score higher: " + early.totalScore + " vs "
                + delayed.totalScore, early.totalScore > delayed.totalScore);
        // The whole gap is the urgency penalty: 80 (turn 2) vs 620 (turn 5)
        assertEquals(-80, early.encounterScore);
        assertEquals(-620, delayed.encounterScore);
        assertEquals(540, early.totalScore - delayed.totalScore);
    }

    @Test
    public void firstTurnHasNoDelayPenalty() {
        TestStateBuilder.Monster nob = nob(80, 0);
        SaveState start = TestStateBuilder.state(70, 70, nob);
        SaveState current = TestStateBuilder.state(70, 70, 0, 1, nob);

        assertEquals(0, TacticalEvaluator.evaluate(start, current, 70).encounterScore);
    }

    @Test
    public void deadNobIsNotPenalized() {
        TestStateBuilder.Monster nob = nob(80, 0);
        SaveState start = TestStateBuilder.state(70, 70, nob);
        TestStateBuilder.Monster deadNob = nob(0, 0);
        SaveState lateWithDeadNob = TestStateBuilder.state(70, 70, 0, 9, deadNob);

        assertEquals(0, TacticalEvaluator.evaluate(start, lateWithDeadNob, 70).encounterScore);
    }

    // ------------------------------------------------------------------
    // Test B - Strength is not double-penalized by the profile
    // ------------------------------------------------------------------

    @Test
    public void strengthConsequencesComeFromThreatEvaluatorNotTheProfile() {
        TestStateBuilder.Monster nobNoStrength = nob(80, 0);
        TestStateBuilder.Monster nobWithStrength = nob(80, 4);
        SaveState start = TestStateBuilder.state(70, 70, nobNoStrength);

        EvaluationResult lowStrength = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(70, 70, 0, 3, nobNoStrength), 70);
        EvaluationResult highStrength = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(70, 70, 0, 3, nobWithStrength), 70);

        // The profile itself must not score Strength (no double counting)
        assertEquals("encounterScore must not depend on Nob Strength",
                lowStrength.encounterScore, highStrength.encounterScore);
        // The generic evaluator does: +4 strength raises immediate damage
        // (4 * IMMEDIATE_THREAT_WEIGHT) and scaling threat (4 * SCALING_THREAT_WEIGHT),
        // pushing the (negative) threat score further down.
        assertEquals(-4 * (TacticalEvaluator.IMMEDIATE_THREAT_WEIGHT
                        + TacticalEvaluator.SCALING_THREAT_WEIGHT),
                highStrength.threatScore - lowStrength.threatScore);
        assertTrue(highStrength.totalScore < lowStrength.totalScore);
    }

    // ------------------------------------------------------------------
    // Test C - death still dominates
    // ------------------------------------------------------------------

    @Test
    public void deadPlayerStillDominatesEvenAgainstNob() {
        TestStateBuilder.Monster nob = nob(80, 0);
        SaveState start = TestStateBuilder.state(70, 70, nob);

        EvaluationResult dead = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(0, 70, 0, 10, nob), 70);
        EvaluationResult living = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(30, 70, 0, 10, nob), 70);

        assertTrue("death must be far below any living state: " + dead.totalScore + " vs "
                + living.totalScore, living.totalScore - dead.totalScore > 500_000);
    }
}

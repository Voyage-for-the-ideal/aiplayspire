package battleaimod.evaluation.encounter;

import battleaimod.evaluation.EvaluationResult;
import battleaimod.evaluation.TacticalEvaluator;
import battleaimod.evaluation.TestStateBuilder;
import org.junit.Test;
import savestate.SaveState;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Lagavulin: the sleeping phase is a free setup window.  Keeping it asleep is
 * valuable, but a large burst can still justify waking it.
 */
public class LagavulinProfileTest {

    // ------------------------------------------------------------------
    // Test A - setup beats tiny poke
    // ------------------------------------------------------------------

    @Test
    public void sleepingSetupBeatsTinyPokeWake() {
        SaveState start = TestStateBuilder.state(70, 70, TestStateBuilder.lagavulin(110, true));

        // A: still asleep, setup window intact
        EvaluationResult asleep = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(70, 70, TestStateBuilder.lagavulin(110, true)), 70);
        // B: tiny poke (5 damage) woke it up, barely any progress
        TestStateBuilder.Monster awakePoked = TestStateBuilder.lagavulin(105, false);
        awakePoked.intent = "BUFF";
        EvaluationResult poked = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(70, 70, awakePoked), 70);

        assertTrue("keeping the setup window must beat a tiny poke: " + asleep.totalScore
                + " vs " + poked.totalScore, asleep.totalScore > poked.totalScore);
        assertEquals(LagavulinProfile.LAGAVULIN_SETUP_WINDOW_VALUE, asleep.encounterScore);
        assertEquals(0, poked.encounterScore);
    }

    // ------------------------------------------------------------------
    // Test B - burst can beat setup
    // ------------------------------------------------------------------

    @Test
    public void largeBurstCanJustifyWakingLagavulin() {
        SaveState start = TestStateBuilder.state(70, 70, TestStateBuilder.lagavulin(110, true));

        // A: keep setting up while it sleeps
        EvaluationResult asleep = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(70, 70, TestStateBuilder.lagavulin(110, true)), 70);
        // B: big burst (100 damage) wakes it but nearly kills it
        TestStateBuilder.Monster awakeBurst = TestStateBuilder.lagavulin(10, false);
        awakeBurst.intent = "ATTACK";
        awakeBurst.baseDamage = 18;
        EvaluationResult burst = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(70, 70, awakeBurst), 70);

        assertTrue("a large burst must be able to beat setup: " + burst.totalScore + " vs "
                + asleep.totalScore, burst.totalScore > asleep.totalScore);
        // The burst wins through progress (100 * DAMAGE_PROGRESS_WEIGHT) minus
        // the now-facing attack (18 * IMMEDIATE_THREAT_WEIGHT) and the lost
        // setup window.
        assertEquals(100 * TacticalEvaluator.DAMAGE_PROGRESS_WEIGHT
                        - 18 * TacticalEvaluator.IMMEDIATE_THREAT_WEIGHT
                        - LagavulinProfile.LAGAVULIN_SETUP_WINDOW_VALUE,
                burst.totalScore - asleep.totalScore);
    }

    // ------------------------------------------------------------------
    // Test C - sleep state comes from the saved state, not the intent
    // ------------------------------------------------------------------

    @Test
    public void sleepIsReadFromSavedStateNotIntent() {
        SaveState start = TestStateBuilder.state(70, 70, TestStateBuilder.lagavulin(110, true));

        // Awake (asleep=false) but with a SLEEP intent: the profile must NOT be
        // fooled by the intent, so no setup-window value may be awarded.
        TestStateBuilder.Monster awakeWithSleepIntent = TestStateBuilder.lagavulin(110, false);
        EvaluationResult awake = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(70, 70, awakeWithSleepIntent), 70);
        assertEquals("awake Lagavulin must not get setup-window value", 0,
                awake.encounterScore);

        // And asleep=true is recognized regardless of what the intent says
        TestStateBuilder.Monster asleep = TestStateBuilder.lagavulin(110, true);
        EvaluationResult asleepResult = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(70, 70, asleep), 70);
        assertEquals(LagavulinProfile.LAGAVULIN_SETUP_WINDOW_VALUE,
                asleepResult.encounterScore);
    }

    @Test
    public void deadLagavulinGetsNoProfileAdjustment() {
        SaveState start = TestStateBuilder.state(70, 70, TestStateBuilder.lagavulin(110, true));
        EvaluationResult dead = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(70, 70, TestStateBuilder.lagavulin(0, false)), 70);
        assertEquals(0, dead.encounterScore);
    }
}

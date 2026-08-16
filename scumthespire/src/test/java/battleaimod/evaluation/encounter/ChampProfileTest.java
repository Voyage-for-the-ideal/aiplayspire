package battleaimod.evaluation.encounter;

import battleaimod.evaluation.EvaluationResult;
import battleaimod.evaluation.TacticalEvaluator;
import battleaimod.evaluation.TestStateBuilder;
import org.junit.Test;
import savestate.SaveState;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The Champ: phase-transition debt.  Crossing the 50% line into Anger is a
 * cost that scales down with phase depth; EXECUTE risk belongs entirely to the
 * exact intent damage in ThreatEvaluator.
 */
public class ChampProfileTest {

    private static final byte ANGER = 7;
    private static final byte EXECUTE = 3;
    private static final byte HEAVY_SLASH = 1;

    private static final int MAX_HP = 500; // half = 250

    private static SaveState champState(int hp, boolean thresholdReached, byte nextMove) {
        TestStateBuilder.Monster champ = TestStateBuilder.champ(hp, thresholdReached, nextMove);
        champ.maxHp = MAX_HP;
        return TestStateBuilder.state(70, 70, champ);
    }

    @Test
    public void atExactlyHalfHpDoesNotTriggerPenalty() {
        // The game checks currentHealth*2 < maxHealth (strict): exactly half
        // must not be treated as a crossed threshold.
        EvaluationResult exactlyHalf = TacticalEvaluator.evaluate(
                champState(MAX_HP, false, HEAVY_SLASH),
                champState(MAX_HP / 2, false, HEAVY_SLASH), 70);
        assertEquals(0, exactlyHalf.encounterScore);

        // Even with the flag set, an ANGER at exactly half pays the full debt
        // (depth 0) - the flag is the real trigger.
        EvaluationResult flagged = TacticalEvaluator.evaluate(
                champState(MAX_HP, false, HEAVY_SLASH),
                champState(MAX_HP / 2, true, ANGER), 70);
        assertEquals(-ChampProfile.SHALLOW_TRANSITION_PENALTY, flagged.encounterScore);
    }

    @Test
    public void shallowTransitionIsWorseThanHolding() {
        EvaluationResult holding = TacticalEvaluator.evaluate(
                champState(MAX_HP, false, HEAVY_SLASH),
                champState(251, false, HEAVY_SLASH), 70);
        EvaluationResult shallow = TacticalEvaluator.evaluate(
                champState(MAX_HP, false, HEAVY_SLASH),
                champState(240, true, ANGER), 70);

        assertEquals(0, holding.encounterScore);
        assertEquals(-400, shallow.encounterScore); // depth 10 -> 600 - 200
        assertTrue("holding above the line must beat a shallow cross",
                holding.totalScore > shallow.totalScore);
    }

    @Test
    public void deepBurstCanBeatHolding() {
        EvaluationResult holding = TacticalEvaluator.evaluate(
                champState(MAX_HP, false, HEAVY_SLASH),
                champState(251, false, HEAVY_SLASH), 70);
        EvaluationResult deep = TacticalEvaluator.evaluate(
                champState(MAX_HP, false, HEAVY_SLASH),
                champState(100, true, ANGER), 70);

        assertEquals(0, deep.encounterScore); // depth 150 -> debt fully credited
        assertTrue("a 151-HP burst through the line must beat holding: " + deep.totalScore
                + " vs " + holding.totalScore, deep.totalScore > holding.totalScore);
    }

    @Test
    public void shallowIsWorseThanDeep() {
        EvaluationResult shallow = TacticalEvaluator.evaluate(
                champState(MAX_HP, false, HEAVY_SLASH),
                champState(240, true, ANGER), 70);
        EvaluationResult deep = TacticalEvaluator.evaluate(
                champState(MAX_HP, false, HEAVY_SLASH),
                champState(100, true, ANGER), 70);

        assertEquals(-400, shallow.encounterScore);
        assertEquals(0, deep.encounterScore);
        assertTrue(shallow.totalScore < deep.totalScore);
    }

    @Test
    public void executeThreatComesFromExactIntentDamage() {
        EvaluationResult execute22 = executeWithIntentDamage(22);
        EvaluationResult execute33 = executeWithIntentDamage(33);

        // Same phase window: profile stays silent on EXECUTE
        assertEquals("EXECUTE must carry no phase-transition debt", 0,
                execute22.encounterScore);
        assertEquals(execute22.encounterScore, execute33.encounterScore);

        // Two hits of exact per-hit damage: 44 vs 66 incoming
        assertEquals(-2 * 22 * TacticalEvaluator.IMMEDIATE_THREAT_WEIGHT,
                execute22.threatScore);
        assertEquals(-2 * 33 * TacticalEvaluator.IMMEDIATE_THREAT_WEIGHT,
                execute33.threatScore);
        // The total difference comes strictly from Threat (higher danger line
        // also lowers survival) - never from the profile.
        assertEquals(execute33.threatScore - execute22.threatScore
                        + (execute33.survivalScore - execute22.survivalScore),
                execute33.totalScore - execute22.totalScore);
    }

    private static EvaluationResult executeWithIntentDamage(int intentDamage) {
        TestStateBuilder.Monster champ = TestStateBuilder.champ(100, true, EXECUTE);
        champ.maxHp = MAX_HP;
        champ.intent = "ATTACK";
        champ.isMultiDamage = true;
        champ.multiplier = 2;
        champ.intentDamage = intentDamage;
        return TacticalEvaluator.evaluate(
                champState(MAX_HP, false, HEAVY_SLASH),
                TestStateBuilder.state(70, 70, champ), 70);
    }

    @Test
    public void replanInvariance() {
        TestStateBuilder.Monster champ = TestStateBuilder.champ(240, true, ANGER);
        champ.maxHp = MAX_HP;

        SaveState rootAtTurn1 = TestStateBuilder.state(70, 70, 0, 1, champ);
        SaveState rootAtTurn5 = TestStateBuilder.state(55, 70, 0, 5, champ);

        SaveState current = TestStateBuilder.state(70, 70, 0, 6, champ);

        assertEquals(TacticalEvaluator.evaluate(rootAtTurn1, current, 70).encounterScore,
                TacticalEvaluator.evaluate(rootAtTurn5, current, 70).encounterScore);
        assertEquals(-400, TacticalEvaluator.evaluate(rootAtTurn5, current, 70).encounterScore);
    }

    @Test
    public void deadChampHasNoEncounterPenalty() {
        TestStateBuilder.Monster dead = TestStateBuilder.champ(0, true, ANGER);
        EvaluationResult result = TacticalEvaluator.evaluate(
                champState(MAX_HP, false, HEAVY_SLASH),
                TestStateBuilder.state(70, 70, dead), 70);
        assertEquals(0, result.encounterScore);
    }

    @Test
    public void registryResolvesChampProfile() {
        assertSame(ChampProfile.INSTANCE, EncounterRegistry.resolve(
                champState(240, true, ANGER)));
    }

    /** Records the generic baseline and profile breakdowns for the report. */
    @Test
    public void recordBreakdowns() {
        SaveState root = champState(MAX_HP, false, HEAVY_SLASH);
        System.out.println("CHAMP hold   : " + TacticalEvaluator.evaluate(root,
                champState(251, false, HEAVY_SLASH), 70));
        System.out.println("CHAMP shallow: " + TacticalEvaluator.evaluate(root,
                champState(240, true, ANGER), 70));
        System.out.println("CHAMP deep   : " + TacticalEvaluator.evaluate(root,
                champState(100, true, ANGER), 70));
        TestStateBuilder.Monster execute = TestStateBuilder.champ(100, true, EXECUTE);
        execute.intent = "ATTACK";
        execute.isMultiDamage = true;
        execute.multiplier = 2;
        execute.intentDamage = 33;
        System.out.println("CHAMP execute: " + TacticalEvaluator.evaluate(root,
                TestStateBuilder.state(70, 70, execute), 70));
    }
}

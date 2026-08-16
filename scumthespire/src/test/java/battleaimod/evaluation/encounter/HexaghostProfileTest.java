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
 * Hexaghost: cycle pressure (future Inferno proximity), with the opening
 * DIVIDER never confused for an Inferno deadline, no threat double counting,
 * post-Inferno escalation, replan invariance and death handling.
 */
public class HexaghostProfileTest {

    // move constants mirroring HexaghostProfile (verified via bytecode)
    private static final byte DIVIDER = 1;
    private static final byte INFERNO = 6;
    private static final byte SEAR = 4;

    private static SaveState hexaghostState(int hp, int orb, byte nextMove,
                                            boolean burnUpgraded) {
        return TestStateBuilder.state(70, 70,
                TestStateBuilder.hexaghost(hp, orb, nextMove, burnUpgraded));
    }

    // Test A - cycle pressure increases as orbs approach Inferno
    @Test
    public void cyclePressureIncreases() {
        SaveState root = hexaghostState(250, 1, SEAR, false);

        EvaluationResult orb1 = TacticalEvaluator.evaluate(root,
                hexaghostState(250, 1, SEAR, false), 70);
        EvaluationResult orb3 = TacticalEvaluator.evaluate(root,
                hexaghostState(250, 3, SEAR, false), 70);
        EvaluationResult orb5 = TacticalEvaluator.evaluate(root,
                hexaghostState(250, 5, SEAR, false), 70);

        assertEquals(-40, orb1.encounterScore);
        assertEquals(-140, orb3.encounterScore);
        assertEquals(-320, orb5.encounterScore);
        assertTrue(orb1.encounterScore > orb3.encounterScore);
        assertTrue(orb3.encounterScore > orb5.encounterScore);
    }

    // Test B - opening Divider (orb 6, nextMove DIVIDER) is NOT an Inferno
    @Test
    public void openingDividerIsNotInfernoDeadline() {
        SaveState root = hexaghostState(250, 6, DIVIDER, false);
        EvaluationResult divider = TacticalEvaluator.evaluate(root,
                hexaghostState(250, 6, DIVIDER, false), 70);
        assertEquals("DIVIDER must not carry a cycle penalty", 0, divider.encounterScore);

        // Same orb count with an actual INFERNO move is handled differently
        TestStateBuilder.Monster infernoMonster = TestStateBuilder.hexaghost(250, 6, INFERNO, false);
        infernoMonster.isMultiDamage = true;
        infernoMonster.multiplier = 6;
        EvaluationResult inferno = TacticalEvaluator.evaluate(root,
                TestStateBuilder.state(70, 70, infernoMonster), 70);
        assertEquals(0, inferno.encounterScore); // immediate threat takes over
        assertTrue("INFERNO's six hits must dominate threat",
                inferno.threatScore < divider.threatScore);
    }

    // Test C - actual attack damage / Strength is not double counted
    @Test
    public void infernoDamageIsNotDoubleCounted() {
        SaveState root = hexaghostState(250, 6, INFERNO, false);

        TestStateBuilder.Monster strongInferno = TestStateBuilder.hexaghost(250, 6, INFERNO, false);
        strongInferno.baseDamage = 12;
        strongInferno.strength = 5;
        EvaluationResult strong = TacticalEvaluator.evaluate(root,
                TestStateBuilder.state(70, 70, strongInferno), 70);

        EvaluationResult normal = TacticalEvaluator.evaluate(root,
                hexaghostState(250, 6, INFERNO, false), 70);

        // Profile must not re-score damage: encounterScore identical
        assertEquals("encounterScore must not depend on attack damage/strength",
                normal.encounterScore, strong.encounterScore);
        // The total difference comes entirely from ThreatEvaluator
        assertTrue(strong.threatScore < normal.threatScore);
        assertEquals(strong.threatScore - normal.threatScore,
                strong.totalScore - normal.totalScore);
    }

    // Test D - a large burst beats cycle preservation
    @Test
    public void largeBurstBeatsCyclePreservation() {
        SaveState root = hexaghostState(250, 1, SEAR, false);

        // A: early cycle, boss untouched
        EvaluationResult early = TacticalEvaluator.evaluate(root,
                hexaghostState(250, 1, SEAR, false), 70);
        // B: deep cycle but the boss lost 60 HP
        EvaluationResult lateBurst = TacticalEvaluator.evaluate(root,
                hexaghostState(190, 5, SEAR, false), 70);

        assertTrue("a 60-HP burst must beat the cycle pressure: " + lateBurst.totalScore
                + " vs " + early.totalScore, lateBurst.totalScore > early.totalScore);
    }

    // Test E - post-Inferno escalation
    @Test
    public void postInfernoEscalationIsPenalized() {
        SaveState root = hexaghostState(250, 2, SEAR, false);

        EvaluationResult before = TacticalEvaluator.evaluate(root,
                hexaghostState(250, 2, SEAR, false), 70);
        EvaluationResult after = TacticalEvaluator.evaluate(root,
                hexaghostState(250, 2, SEAR, true), 70);

        assertEquals(HexaghostProfile.POST_INFERNO_ESCALATION_PENALTY,
                after.encounterScore - before.encounterScore);
        assertTrue(after.encounterScore < before.encounterScore);
    }

    // Test F - replan invariance
    @Test
    public void replanInvariance() {
        TestStateBuilder.Monster hex = TestStateBuilder.hexaghost(250, 5, SEAR, false);

        SaveState rootAtTurn1 = TestStateBuilder.state(70, 70, 0, 1, hex);
        SaveState rootAtTurn4 = TestStateBuilder.state(60, 70, 0, 4, hex);

        SaveState current = TestStateBuilder.state(70, 70, 0, 7, hex);

        assertEquals(TacticalEvaluator.evaluate(rootAtTurn1, current, 70).encounterScore,
                TacticalEvaluator.evaluate(rootAtTurn4, current, 70).encounterScore);
        assertEquals(-320,
                TacticalEvaluator.evaluate(rootAtTurn4, current, 70).encounterScore);
    }

    // Test G - dead Hexaghost has no profile adjustment
    @Test
    public void deadHexaghostScoresZero() {
        SaveState root = hexaghostState(250, 1, SEAR, false);
        TestStateBuilder.Monster dead = TestStateBuilder.hexaghost(0, 5, SEAR, true);
        EvaluationResult result = TacticalEvaluator.evaluate(root,
                TestStateBuilder.state(70, 70, dead), 70);
        assertEquals(0, result.encounterScore);
    }

    @Test
    public void registryResolvesHexaghostProfile() {
        SaveState state = hexaghostState(250, 1, SEAR, false);
        assertSame(HexaghostProfile.INSTANCE, EncounterRegistry.resolve(state));
    }

    /** Records breakdowns for the report. */
    @Test
    public void recordBreakdowns() {
        SaveState root = hexaghostState(250, 1, SEAR, false);
        System.out.println("HEX early      : " + TacticalEvaluator.evaluate(root,
                hexaghostState(250, 1, SEAR, false), 70));
        System.out.println("HEX late       : " + TacticalEvaluator.evaluate(root,
                hexaghostState(250, 5, SEAR, false), 70));
        System.out.println("HEX divider    : " + TacticalEvaluator.evaluate(root,
                hexaghostState(250, 6, DIVIDER, false), 70));
        TestStateBuilder.Monster inferno = TestStateBuilder.hexaghost(250, 6, INFERNO, false);
        inferno.baseDamage = 6;
        inferno.multiplier = 6;
        inferno.isMultiDamage = true;
        System.out.println("HEX inferno    : " + TacticalEvaluator.evaluate(root,
                TestStateBuilder.state(70, 70, inferno), 70));
    }
}

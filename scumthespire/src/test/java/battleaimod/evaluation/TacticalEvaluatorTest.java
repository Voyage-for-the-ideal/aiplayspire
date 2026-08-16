package battleaimod.evaluation;

import org.junit.Test;
import savestate.SaveState;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * Phase 1 regression tests: the three generic tactical behaviors the evaluator
 * must prove before any encounter-specific work:
 * <ol>
 *   <li>Killing an attacker is defense (kill high-threat low-HP enemy first)</li>
 *   <li>Death risk is non-linear (low-HP losses cost more)</li>
 *   <li>Battle end / lethal is a discrete breakpoint, not linear damage</li>
 * </ol>
 */
public class TacticalEvaluatorTest {

    // ------------------------------------------------------------------
    // Test 1: kill-is-block
    // ------------------------------------------------------------------

    /**
     * Enemy A: 8 HP, intent damage 18.  Enemy B: 40 HP, intent damage 6.
     * Killing A must score higher than dealing more total damage to B.
     */
    @Test
    public void killingHighThreatAttackerBeatsRawDamage() {
        TestStateBuilder.Monster enemyA = TestStateBuilder.attacking(8, 18);
        TestStateBuilder.Monster enemyB = TestStateBuilder.attacking(40, 6);
        SaveState start = TestStateBuilder.state(70, 70, enemyA, enemyB);

        // Path A: kill the 8 HP attacker
        List<TestStateBuilder.Monster> killStateMonsters = TestStateBuilder.copy(
                Arrays.asList(enemyA, enemyB));
        killStateMonsters.get(0).hp = 0;
        SaveState killState = TestStateBuilder.state(70, 70, killStateMonsters);

        // Path B: pump more raw damage into the 40 HP enemy (16 damage)
        List<TestStateBuilder.Monster> damageStateMonsters = TestStateBuilder.copy(
                Arrays.asList(enemyA, enemyB));
        damageStateMonsters.get(1).hp = 24;
        SaveState damageState = TestStateBuilder.state(70, 70, damageStateMonsters);

        EvaluationResult kill = TacticalEvaluator.evaluate(start, killState, 70);
        EvaluationResult damage = TacticalEvaluator.evaluate(start, damageState, 70);

        assertTrue("kill should beat raw damage: " + kill.totalScore + " vs " + damage.totalScore,
                kill.totalScore > damage.totalScore);

        // The kill's value must come from threat removal, not a hard-coded kill
        // bonus: no battle-complete bonus, no near-lethal bonus involved.
        assertEquals(0, kill.lethalScore);
        assertEquals(0, damage.lethalScore);
        assertTrue("threat remaining after kill (" + kill.threatScore
                        + ") must be higher than after raw damage (" + damage.threatScore + ")",
                kill.threatScore > damage.threatScore);
        assertTrue("damage path dealt more raw damage",
                damage.damageProgressScore > kill.damageProgressScore);
        assertEquals("same player state -> same survival",
                kill.survivalScore, damage.survivalScore);
    }

    // ------------------------------------------------------------------
    // Test 2: death risk is non-linear
    // ------------------------------------------------------------------

    /**
     * The same 4 HP loss must be strictly more expensive at low HP (5 -> 1)
     * than at healthy HP (30 -> 26).
     */
    @Test
    public void hpLossIsMoreExpensiveNearDeath() {
        TestStateBuilder.Monster passive = TestStateBuilder.monster(40);
        SaveState start = TestStateBuilder.state(70, 70, passive);

        EvaluationResult hp30 = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(30, 70, passive), 70);
        EvaluationResult hp26 = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(26, 70, passive), 70);
        EvaluationResult hp5 = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(5, 70, passive), 70);
        EvaluationResult hp1 = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(1, 70, passive), 70);

        int healthyLoss = hp30.totalScore - hp26.totalScore;
        int nearDeathLoss = hp5.totalScore - hp1.totalScore;

        assertTrue("low-HP loss must be strictly more expensive: " + nearDeathLoss + " vs "
                + healthyLoss, nearDeathLoss > healthyLoss);
        assertTrue("near-death loss should be at least 3x healthy loss: " + nearDeathLoss + " vs "
                + healthyLoss, nearDeathLoss >= 3 * healthyLoss);
    }

    /** Death is overwhelmingly worse than any living state. */
    @Test
    public void deathIsOverwhelminglyWorseThanLiving() {
        TestStateBuilder.Monster passive = TestStateBuilder.monster(40);
        SaveState start = TestStateBuilder.state(70, 70, passive);

        EvaluationResult dead = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(0, 70, passive), 70);
        EvaluationResult barelyAlive = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(1, 70, passive), 70);

        assertTrue("dead must be far below any living state: " + dead.totalScore + " vs "
                + barelyAlive.totalScore, barelyAlive.totalScore - dead.totalScore > 500_000);
        assertEquals(TacticalEvaluator.DEATH_PENALTY, dead.survivalScore);
    }

    // ------------------------------------------------------------------
    // Test 3: battle-end / lethal breakpoint
    // ------------------------------------------------------------------

    /**
     * Enemy at 0 HP (battle over) must be far better than the same enemy at
     * 1 HP, even though only 1 damage separates them.
     */
    @Test
    public void battleCompleteIsADiscreteBreakpoint() {
        TestStateBuilder.Monster enemy = TestStateBuilder.attacking(8, 6);
        SaveState start = TestStateBuilder.state(70, 70, enemy);

        EvaluationResult dead = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(70, 70, TestStateBuilder.monster(0)), 70);
        EvaluationResult alive = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(70, 70, TestStateBuilder.attacking(1, 6)), 70);

        assertTrue("battle complete must dominate: " + dead.totalScore + " vs " + alive.totalScore,
                dead.totalScore - alive.totalScore > 500_000);
        assertEquals(TacticalEvaluator.BATTLE_COMPLETE_BONUS, dead.lethalScore);
        assertFalse("still alive -> no battle-complete bonus",
                alive.lethalScore >= TacticalEvaluator.BATTLE_COMPLETE_BONUS);
    }

    /** Overkill on an already-dead enemy must not add value. */
    @Test
    public void overkillIsNotRewarded() {
        TestStateBuilder.Monster enemy = TestStateBuilder.attacking(8, 6);
        SaveState start = TestStateBuilder.state(70, 70, enemy);

        EvaluationResult exactlyDead = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(70, 70, TestStateBuilder.monster(0)), 70);
        EvaluationResult overkilled = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(70, 70, TestStateBuilder.monster(-10)), 70);

        assertEquals("overkill must not increase the score", exactlyDead.totalScore,
                overkilled.totalScore);
        assertEquals("damage progress capped at starting effective HP", 8 * 10,
                exactlyDead.damageProgressScore);
    }

    /**
     * Invariant: killing an attacking enemy must beat leaving it at 1 HP, and
     * the difference must come from threat removal, not a fixed kill bonus.
     * A second (passive) enemy keeps the battle from ending so no
     * battle-complete bonus contaminates the comparison.
     */
    @Test
    public void killingAttackerAt1HpBeatsLeavingItAliveViaThreatRemoval() {
        TestStateBuilder.Monster attacker = TestStateBuilder.attacking(8, 18);
        TestStateBuilder.Monster passive = TestStateBuilder.monster(40);
        SaveState start = TestStateBuilder.state(70, 70, attacker, passive);

        // State A: the attacker survives at 1 HP and is still attacking
        EvaluationResult atOneHp = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(70, 70,
                        TestStateBuilder.attacking(1, 18), TestStateBuilder.monster(40)), 70);
        // State B: the same enemy is dead; everything else identical
        EvaluationResult dead = TacticalEvaluator.evaluate(start,
                TestStateBuilder.state(70, 70,
                        TestStateBuilder.monster(0), TestStateBuilder.monster(40)), 70);

        assertTrue("killing the attacker must score higher: " + dead.totalScore + " vs "
                + atOneHp.totalScore, dead.totalScore > atOneHp.totalScore);
        // No fixed kill bonus and no battle-complete bonus in either state:
        // the whole gain is threat removed + the final point of progress.
        assertEquals(0, dead.lethalScore);
        assertEquals(0, atOneHp.lethalScore);
        assertTrue("threat removal must drive the gain: " + dead.threatScore + " vs "
                + atOneHp.threatScore, dead.threatScore > atOneHp.threatScore);
        // The gap is exactly threat removed (18 * IMMEDIATE_THREAT_WEIGHT)
        // plus the last point of progress (8 HP dealt vs 7).
        assertEquals(18 * TacticalEvaluator.IMMEDIATE_THREAT_WEIGHT
                        + TacticalEvaluator.DAMAGE_PROGRESS_WEIGHT,
                dead.totalScore - atOneHp.totalScore);
    }

    // ------------------------------------------------------------------
    // Breakdown integrity
    // ------------------------------------------------------------------

    @Test
    public void totalScoreIsSumOfComponents() {
        TestStateBuilder.Monster enemy = TestStateBuilder.attacking(20, 12);
        SaveState start = TestStateBuilder.state(70, 70, enemy);
        SaveState current = TestStateBuilder.state(64, 70,
                TestStateBuilder.attacking(9, 12));

        EvaluationResult result = TacticalEvaluator.evaluate(start, current, 70);

        assertEquals(result.survivalScore + result.damageProgressScore + result.lethalScore
                + result.threatScore + result.scalingScore + result.resourceScore
                + result.encounterScore, result.totalScore);
        // encounter adjustment is 0 until Phase 2
        assertEquals(0, result.encounterScore);
    }

}

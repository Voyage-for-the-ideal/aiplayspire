package battleaimod.evaluation;

import org.junit.Test;
import savestate.SaveState;

import static org.junit.Assert.assertEquals;

/**
 * Enemy burden delta semantics and the block single-counting invariant:
 * block contributes to burden (killability) exactly once, and never to threat.
 */
public class BurdenDeltaTest {

    // A - a summon increases burden (negative delta, not clamped to 0)
    @Test
    public void summonIncreasesBurden() {
        SaveState root = TestStateBuilder.state(70, 70, TestStateBuilder.monster(100));

        SaveState damagedPlusSummon = TestStateBuilder.state(70, 70,
                TestStateBuilder.monster(70), TestStateBuilder.monster(40));
        assertEquals(-10, CombatFeatures.extract(damagedPlusSummon, root, 70).enemyBurdenDelta);
    }

    // B - enemy gaining block increases burden
    @Test
    public void enemyGainingBlockIncreasesBurden() {
        TestStateBuilder.Monster rootEnemy = TestStateBuilder.monster(50);
        SaveState root = TestStateBuilder.state(70, 70, rootEnemy);

        TestStateBuilder.Monster blocked = TestStateBuilder.monster(50);
        blocked.block = 20;
        SaveState current = TestStateBuilder.state(70, 70, blocked);
        assertEquals(-20, CombatFeatures.extract(current, root, 70).enemyBurdenDelta);
    }

    // C - block disappearing reduces burden
    @Test
    public void blockDisappearingReducesBurden() {
        TestStateBuilder.Monster rootEnemy = TestStateBuilder.monster(50);
        rootEnemy.block = 20;
        SaveState root = TestStateBuilder.state(70, 70, rootEnemy);

        SaveState current = TestStateBuilder.state(70, 70, TestStateBuilder.monster(50));
        assertEquals(20, CombatFeatures.extract(current, root, 70).enemyBurdenDelta);
    }

    // D - block is counted exactly once: never in threat, only in the burden delta
    @Test
    public void blockIsCountedExactlyOnce() {
        TestStateBuilder.Monster attacker = TestStateBuilder.attacking(50, 6);
        SaveState root = TestStateBuilder.state(70, 70, attacker);

        // Same HP, same attack, same strength - only block differs
        TestStateBuilder.Monster noBlock = TestStateBuilder.attacking(50, 6);
        TestStateBuilder.Monster withBlock = TestStateBuilder.attacking(50, 6);
        withBlock.block = 20;

        EvaluationResult plain = TacticalEvaluator.evaluate(root,
                TestStateBuilder.state(70, 70, noBlock), 70);
        EvaluationResult blocked = TacticalEvaluator.evaluate(root,
                TestStateBuilder.state(70, 70, withBlock), 70);

        // Threat must be identical (block is not threat)
        assertEquals("block must not change threat", plain.threatScore, blocked.threatScore);
        // The only total-score difference is the burden delta * progress weight
        assertEquals(-20 * TacticalEvaluator.DAMAGE_PROGRESS_WEIGHT,
                blocked.totalScore - plain.totalScore);
    }

    // E - Slime Boss deep/shallow regressions keep passing
    @Test
    public void slimeDeepVsShallowStillHolds() {
        TestStateBuilder.Monster boss = TestStateBuilder.monster(150);
        SaveState root = TestStateBuilder.state(70, 70, boss);

        SaveState shallow = TestStateBuilder.state(70, 70,
                TestStateBuilder.monster(0), TestStateBuilder.monster(74),
                TestStateBuilder.monster(74));
        SaveState deep = TestStateBuilder.state(70, 70,
                TestStateBuilder.monster(0), TestStateBuilder.monster(30),
                TestStateBuilder.monster(30));

        EvaluationResult shallowResult = TacticalEvaluator.evaluate(root, shallow, 70);
        EvaluationResult deepResult = TacticalEvaluator.evaluate(root, deep, 70);

        assertEquals(2, shallowResult.damageProgressScore / TacticalEvaluator.DAMAGE_PROGRESS_WEIGHT);
        assertEquals(90, deepResult.damageProgressScore / TacticalEvaluator.DAMAGE_PROGRESS_WEIGHT);
        org.junit.Assert.assertTrue(deepResult.totalScore > shallowResult.totalScore);
    }
}

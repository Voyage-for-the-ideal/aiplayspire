package battleaimod.evaluation;

import org.junit.Test;
import savestate.SaveState;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Feature extraction sanity checks (direct state reads, no JSON round-trip). */
public class CombatFeaturesTest {

    @Test
    public void extractsPlayerAndEnemyAggregates() {
        SaveState state = TestStateBuilder.state(64, 70,
                TestStateBuilder.attacking(8, 18), TestStateBuilder.attacking(40, 6));

        CombatFeatures features = CombatFeatures.extract(state, state, 70);

        assertEquals(64, features.playerCurrentHp);
        assertEquals(70, features.playerMaxHp);
        assertEquals(6, features.hpLostFromCombatStart);
        assertEquals(2, features.aliveEnemyCount);
        assertEquals(0, features.deadEnemyCount);
        assertEquals(48, features.totalEnemyHp);
        assertEquals(24, features.currentIncomingDamage);
        assertEquals(2, features.currentIncomingHitCount);
        assertEquals(18, features.highestEnemyIncomingDamage);
        assertEquals(24 * TacticalEvaluator.IMMEDIATE_THREAT_WEIGHT, features.aliveThreat);
        assertEquals(18 * TacticalEvaluator.IMMEDIATE_THREAT_WEIGHT,
                features.highestSingleEnemyThreat);
        assertFalse(features.allEnemiesDead);
    }

    @Test
    public void playerBlockAbsorbsIncomingDamage() {
        // 18 incoming vs 10 block -> 8 effective
        SaveState state = TestStateBuilder.state(64, 70, 10,
                TestStateBuilder.attacking(8, 18));
        CombatFeatures features = CombatFeatures.extract(state, state, 70);
        assertEquals(10, features.playerBlock);
        assertEquals(8, features.currentIncomingDamage);

        // no block -> full 18 incoming
        SaveState noBlockState = TestStateBuilder.state(64, 70,
                TestStateBuilder.attacking(8, 18));
        CombatFeatures noBlockFeatures = CombatFeatures.extract(noBlockState, noBlockState, 70);
        assertEquals(18, noBlockFeatures.currentIncomingDamage);
    }

    @Test
    public void damageProgressIsCappedPerEnemy() {
        TestStateBuilder.Monster enemy = TestStateBuilder.attacking(8, 6);
        SaveState start = TestStateBuilder.state(70, 70, enemy);

        // enemy dropped to 3 HP -> 5 progress
        CombatFeatures partial = CombatFeatures.extract(
                TestStateBuilder.state(70, 70, TestStateBuilder.attacking(3, 6)), start, 70);
        assertEquals(5, partial.damageDealtThisCombat);

        // enemy dead -> full 8 progress, capped even when overkilled to -10
        CombatFeatures dead = CombatFeatures.extract(
                TestStateBuilder.state(70, 70, TestStateBuilder.monster(-10)), start, 70);
        assertEquals(8, dead.damageDealtThisCombat);
    }

    @Test
    public void deadEnemyDoesNotCountAsAlive() {
        SaveState state = TestStateBuilder.state(70, 70,
                TestStateBuilder.monster(0), TestStateBuilder.attacking(20, 6));
        CombatFeatures features = CombatFeatures.extract(state, state, 70);
        assertEquals(1, features.aliveEnemyCount);
        assertEquals(1, features.deadEnemyCount);
        assertEquals(20, features.totalEnemyHp);
        assertEquals(6 * TacticalEvaluator.IMMEDIATE_THREAT_WEIGHT, features.aliveThreat);
        assertFalse(features.allEnemiesDead);
    }

    @Test
    public void allEnemiesDeadDetected() {
        SaveState state = TestStateBuilder.state(70, 70,
                TestStateBuilder.monster(0), TestStateBuilder.monster(0));
        CombatFeatures features = CombatFeatures.extract(state, state, 70);
        assertTrue(features.allEnemiesDead);
        assertEquals(0, features.aliveEnemyCount);
        assertEquals(2, features.deadEnemyCount);
    }
}

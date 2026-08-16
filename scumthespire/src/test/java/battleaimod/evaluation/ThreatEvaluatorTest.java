package battleaimod.evaluation;

import org.junit.Test;
import savestate.SaveState;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Unit tests for threat: damage, strength scaling and block components. */
public class ThreatEvaluatorTest {

    @Test
    public void attackIntentYieldsImmediateThreat() {
        SaveState state = TestStateBuilder.state(70, 70,
                TestStateBuilder.attacking(20, 18));
        assertEquals(18, ThreatEvaluator.immediateDamageOf(
                state.curMapNodeState.monsterData.get(0)));
        assertEquals(18 * TacticalEvaluator.IMMEDIATE_THREAT_WEIGHT,
                ThreatEvaluator.totalThreat(state));
    }

    @Test
    public void nonAttackIntentHasNoImmediateThreat() {
        SaveState state = TestStateBuilder.state(70, 70, TestStateBuilder.monster(20));
        assertEquals(0, ThreatEvaluator.totalThreat(state));
    }

    @Test
    public void multiHitMultipliesThreat() {
        TestStateBuilder.Monster attacker = TestStateBuilder.attacking(20, 6);
        attacker.isMultiDamage = true;
        attacker.multiplier = 3;
        SaveState state = TestStateBuilder.state(70, 70, attacker);
        assertEquals(18, ThreatEvaluator.immediateDamageOf(
                state.curMapNodeState.monsterData.get(0)));
        assertEquals(3, ThreatEvaluator.incomingHitCountOf(
                state.curMapNodeState.monsterData.get(0)));
    }

    @Test
    public void strengthAddsScalingThreat() {
        TestStateBuilder.Monster attacker = TestStateBuilder.attacking(20, 6);
        attacker.strength = 4;
        SaveState state = TestStateBuilder.state(70, 70, attacker);
        // immediate (6+4) * IMMEDIATE_THREAT_WEIGHT, scaling 4 * SCALING_THREAT_WEIGHT
        assertEquals(10 * TacticalEvaluator.IMMEDIATE_THREAT_WEIGHT
                        + 4 * TacticalEvaluator.SCALING_THREAT_WEIGHT,
                ThreatEvaluator.threatOf(state.curMapNodeState.monsterData.get(0)));
    }

    @Test
    public void blockAddsThreatBecauseEnemySurvivesLonger() {
        TestStateBuilder.Monster attacker = TestStateBuilder.attacking(20, 6);
        attacker.block = 10;
        SaveState state = TestStateBuilder.state(70, 70, attacker);
        assertEquals(6 * TacticalEvaluator.IMMEDIATE_THREAT_WEIGHT
                        + 10 * TacticalEvaluator.BLOCK_THREAT_WEIGHT,
                ThreatEvaluator.threatOf(state.curMapNodeState.monsterData.get(0)));
    }

    @Test
    public void deadEnemiesHaveNoThreat() {
        TestStateBuilder.Monster dead = TestStateBuilder.attacking(0, 18);
        SaveState state = TestStateBuilder.state(70, 70, dead);
        assertEquals(0, ThreatEvaluator.totalThreat(state));
        assertEquals(0, ThreatEvaluator.highestSingleThreat(state));
    }

    @Test
    public void highestSingleThreatTracksTheWorstEnemy() {
        SaveState state = TestStateBuilder.state(70, 70,
                TestStateBuilder.attacking(8, 18), TestStateBuilder.attacking(40, 6));
        assertEquals(18 * TacticalEvaluator.IMMEDIATE_THREAT_WEIGHT,
                ThreatEvaluator.highestSingleThreat(state));
    }

    @Test
    public void threatScaleAllowsKillToBeatRawDamage() {
        // Sanity of the weight balance behind Test 1: threat(18 dmg attacker)
        // must outweigh the progress gained by dealing 8 more raw damage.
        int killValue = 18 * TacticalEvaluator.IMMEDIATE_THREAT_WEIGHT
                - 8 * TacticalEvaluator.DAMAGE_PROGRESS_WEIGHT;
        int damageValue = 8 * TacticalEvaluator.DAMAGE_PROGRESS_WEIGHT;
        assertTrue(killValue > damageValue);
    }
}

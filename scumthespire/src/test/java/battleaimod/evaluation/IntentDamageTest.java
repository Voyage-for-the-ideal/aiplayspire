package battleaimod.evaluation;

import org.junit.Test;
import savestate.SaveState;

import static org.junit.Assert.assertEquals;

/**
 * Exact intent damage semantics: the game-computed intentDmg wins over the
 * reconstructed baseDamage + Strength value, multi-hit multiplies the exact
 * per-hit value, legacy states without intent_damage keep the old fallback,
 * and the field survives a JSON round-trip.
 */
public class IntentDamageTest {

    // 1 - exact intent damage overrides the reconstructed value
    @Test
    public void exactIntentDamageOverridesReconstructedValue() {
        // baseDamage 6 + Strength 4 would reconstruct as 10; the game says 22
        TestStateBuilder.Monster attacker = TestStateBuilder.attacking(40, 6);
        attacker.strength = 4;
        attacker.intentDamage = 22;
        SaveState state = TestStateBuilder.state(70, 70, attacker);

        assertEquals(22, ThreatEvaluator.immediateDamageOf(
                state.curMapNodeState.monsterData.get(0)));
    }

    // 2 - multi-hit uses the exact per-hit value
    @Test
    public void multiHitMultipliesExactPerHitDamage() {
        TestStateBuilder.Monster attacker = TestStateBuilder.attacking(40, 6);
        attacker.strength = 4;
        attacker.intentDamage = 22;
        attacker.isMultiDamage = true;
        attacker.multiplier = 3;
        SaveState state = TestStateBuilder.state(70, 70, attacker);

        assertEquals(66, ThreatEvaluator.immediateDamageOf(
                state.curMapNodeState.monsterData.get(0)));
        assertEquals(3, ThreatEvaluator.incomingHitCountOf(
                state.curMapNodeState.monsterData.get(0)));
    }

    // 3 - legacy JSON without intent_damage falls back to baseDamage + Strength
    @Test
    public void legacyStateWithoutIntentDamageFallsBack() {
        TestStateBuilder.Monster attacker = TestStateBuilder.attacking(40, 6);
        attacker.strength = 4;
        // intentDamage stays -1: the JSON field is omitted
        SaveState state = TestStateBuilder.state(70, 70, attacker);

        assertEquals(10, ThreatEvaluator.immediateDamageOf(
                state.curMapNodeState.monsterData.get(0)));
    }

    // 4 - intent_damage survives a JSON round-trip
    @Test
    public void intentDamageRoundTripsThroughJson() {
        TestStateBuilder.Monster attacker = TestStateBuilder.attacking(40, 6);
        attacker.intentDamage = 33;
        SaveState state = TestStateBuilder.state(70, 70, attacker);

        SaveState roundTripped = new SaveState(state.jsonEncode());
        assertEquals(33, roundTripped.curMapNodeState.monsterData.get(0).getIntentDamage());
        assertEquals(33, ThreatEvaluator.immediateDamageOf(
                roundTripped.curMapNodeState.monsterData.get(0)));
    }
}

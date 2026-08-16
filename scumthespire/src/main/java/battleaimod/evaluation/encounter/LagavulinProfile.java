package battleaimod.evaluation.encounter;

import battleaimod.evaluation.CombatFeatures;
import savestate.SaveState;
import savestate.monsters.MonsterState;
import savestate.monsters.exordium.LagavulinState;

/**
 * Lagavulin: the sleeping phase is a free setup window.
 * <p>
 * While asleep, the player can scale (powers, strength, block) without paying
 * for it.  The generic evaluator already rewards the resulting powers, so this
 * profile only values keeping the window itself open.
 * <p>
 * The bonus is deliberately small enough that a large burst can beat it:
 * waking Lagavulin with big damage is often correct, while a tiny poke that
 * only wakes it is not.
 */
public final class LagavulinProfile implements EncounterProfile {

    public static final LagavulinProfile INSTANCE = new LagavulinProfile();

    /** Value of keeping the sleeping setup window open for another turn. */
    public static final int LAGAVULIN_SETUP_WINDOW_VALUE = 200;

    private LagavulinProfile() {
    }

    @Override
    public int evaluate(SaveState combatStartState, SaveState currentState,
                        CombatFeatures features) {
        if (currentState.curMapNodeState == null
                || currentState.curMapNodeState.monsterData == null) {
            return 0;
        }
        for (MonsterState monster : currentState.curMapNodeState.monsterData) {
            if (monster instanceof LagavulinState && monster.currentHealth > 0) {
                // Read the saved asleep state directly - never infer sleep from
                // the current intent (intents during sleep are unreliable).
                return ((LagavulinState) monster).isAsleep()
                        ? LAGAVULIN_SETUP_WINDOW_VALUE : 0;
            }
        }
        return 0;
    }
}

package battleaimod.evaluation.encounter;

import battleaimod.evaluation.CombatFeatures;
import savestate.SaveState;

/**
 * Encounter-specific adjustment on top of the generic tactical evaluation.
 * <p>
 * A profile must NOT re-implement generic responsibilities (survival, threat,
 * damage progress, lethal, resources).  It only expresses which value
 * relationships this encounter breaks - e.g. "time is expensive" (Gremlin Nob)
 * or "the sleeping phase is a free setup window" (Lagavulin).
 * <p>
 * Profiles are stateless: one shared instance per encounter, resolved through
 * {@link EncounterRegistry}.
 */
public interface EncounterProfile {

    /**
     * Returns the encounter-specific score adjustment for {@code currentState}.
     *
     * @param searchRootState root state of the current search segment
     *                        (BattleAiController.startingState), NOT necessarily
     *                        the combat-start turn; may be null
     * @param currentState    state being evaluated
     * @param features        extracted combat features (current state)
     */
    int evaluate(SaveState searchRootState, SaveState currentState, CombatFeatures features);
}

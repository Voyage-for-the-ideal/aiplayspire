package battleaimod.evaluation.encounter;

import battleaimod.evaluation.CombatFeatures;
import savestate.SaveState;

/** No encounter-specific adjustment: generic evaluation alone decides. */
public final class DefaultEncounterProfile implements EncounterProfile {

    public static final DefaultEncounterProfile INSTANCE = new DefaultEncounterProfile();

    private DefaultEncounterProfile() {
    }

    @Override
    public int evaluate(SaveState combatStartState, SaveState currentState,
                        CombatFeatures features) {
        return 0;
    }
}

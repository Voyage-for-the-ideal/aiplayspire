package battleaimod.evaluation.encounter;

import savestate.SaveState;
import savestate.monsters.MonsterState;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps monster ids to their shared, stateless {@link EncounterProfile}.
 * <p>
 * Resolution is a single pass over the monster list with a static map lookup:
 * no reflection, no JSON, no per-evaluation allocation beyond the map lookup
 * itself.  Encounters without a profile fall back to
 * {@link DefaultEncounterProfile} (score 0).
 */
public final class EncounterRegistry {

    private static final Map<String, EncounterProfile> PROFILES = new HashMap<>();

    static {
        PROFILES.put("GremlinNob", GremlinNobProfile.INSTANCE);
        PROFILES.put("Lagavulin", LagavulinProfile.INSTANCE);
    }

    private EncounterRegistry() {
    }

    /**
     * Returns the profile for the encounter present in {@code state}.  With
     * multiple monsters the first registered match wins (Nob and Lagavulin are
     * single-monster elites, so ordering is irrelevant for Phase 2A).
     */
    public static EncounterProfile resolve(SaveState state) {
        if (state == null || state.curMapNodeState == null
                || state.curMapNodeState.monsterData == null) {
            return DefaultEncounterProfile.INSTANCE;
        }
        for (MonsterState monster : state.curMapNodeState.monsterData) {
            EncounterProfile profile = PROFILES.get(monster.id);
            if (profile != null) {
                return profile;
            }
        }
        return DefaultEncounterProfile.INSTANCE;
    }
}

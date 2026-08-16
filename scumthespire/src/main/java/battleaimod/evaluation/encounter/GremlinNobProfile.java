package battleaimod.evaluation.encounter;

import battleaimod.evaluation.CombatFeatures;
import savestate.SaveState;
import savestate.monsters.MonsterState;

/**
 * Gremlin Nob: time is expensive.
 * <p>
 * The generic evaluator already handles the consequences of Skill plays - the
 * simulation raises Nob's Strength and ThreatEvaluator converts it into higher
 * immediate and scaling threat.  This profile must NOT re-score Strength.
 * <p>
 * It only adds combat urgency: every turn Nob stays alive costs more, with a
 * slightly escalating curve, so the search prefers tempo lines without ever
 * hard-forbidding Skills.
 * <p>
 * Urgency is based on the ABSOLUTE combat turn (GameActionManager.turn starts
 * at 1 for the player's first turn), never on the search root: replans create
 * new search roots mid-combat, so root-relative turn counting would reset the
 * urgency and let replanned searches "forget" how long the fight has run.
 */
public final class GremlinNobProfile implements EncounterProfile {

    public static final GremlinNobProfile INSTANCE = new GremlinNobProfile();

    /** First turn of a combat is turn 1 (AbstractDungeon.resetPlayer). */
    public static final int FIRST_COMBAT_TURN = 1;

    /** Base per-turn penalty while Nob is alive. */
    public static final int NOB_TURN_PENALTY_BASE = 80;
    /** Extra penalty added for each turn beyond the first (escalation). */
    public static final int NOB_TURN_ESCALATION = 100;

    private GremlinNobProfile() {
    }

    @Override
    public int evaluate(SaveState searchRootState, SaveState currentState,
                        CombatFeatures features) {
        // searchRootState is intentionally unused: urgency must not depend on
        // where the current search segment started (replan invariance).
        if (!nobAlive(currentState)) {
            return 0;
        }
        int turnsElapsed = Math.max(0, currentState.turn - FIRST_COMBAT_TURN);
        if (turnsElapsed <= 0) {
            return 0;
        }
        // Linear base plus escalation: turn 1 -> 80, turn 2 -> 260,
        // turn 3 -> 440, turn 4 -> 620 ...
        int penalty = turnsElapsed * NOB_TURN_PENALTY_BASE
                + Math.max(0, turnsElapsed - 1) * NOB_TURN_ESCALATION;
        return -penalty;
    }

    private static boolean nobAlive(SaveState state) {
        if (state.curMapNodeState == null || state.curMapNodeState.monsterData == null) {
            return false;
        }
        for (MonsterState monster : state.curMapNodeState.monsterData) {
            if (monster.id.equals("GremlinNob") && monster.currentHealth > 0) {
                return true;
            }
        }
        return false;
    }
}

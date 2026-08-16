package battleaimod.evaluation.encounter;

import battleaimod.evaluation.CombatFeatures;
import savestate.SaveState;
import savestate.monsters.MonsterState;
import savestate.monsters.city.ChampState;

/**
 * The Champ: phase-transition debt only.
 * <p>
 * The generic evaluator already handles HP, block, current intent damage,
 * Strength, damage progress and survival.  It cannot express one thing:
 * crossing the 50% threshold into phase 2 is itself a cost, and Anger (a BUFF
 * with no immediate damage) makes a barely-crossed transition look too good.
 * <p>
 * State machine (verified against game bytecode):
 * <pre>
 *   getMove: currentHealth*2 < maxHealth (strictly below 50%) and not yet
 *     reached -> thresholdReached = true, nextMove = ANGER (7)
 *   afterwards -> EXECUTE (3): two hits of damage[1].base
 * </pre>
 * This profile only scores the ANGER window:
 * <pre>
 *   phaseDepth = max(0, halfHp - currentHealth)
 *   transitionDebt = max(0, SHALLOW_TRANSITION_PENALTY
 *                        - phaseDepth * PHASE_DEPTH_CREDIT_PER_HP)
 *   encounterScore = -transitionDebt
 * </pre>
 * A shallow cross pays nearly the full penalty; a deep burst crosses with
 * little or no debt.  Outside the ANGER window (EXECUTE, later phase-2 turns,
 * or the boss untouched) the score is 0 - EXECUTE's risk is scored entirely
 * by the exact intent damage in ThreatEvaluator.
 */
public final class ChampProfile implements EncounterProfile {

    public static final ChampProfile INSTANCE = new ChampProfile();

    /** ANGER nextMove (verified via bytecode: setMove(7) on crossing). */
    public static final byte CHAMP_MOVE_ANGER = 7;
    /** EXECUTE nextMove (verified via bytecode: setMove(3, ... two hits)). */
    public static final byte CHAMP_MOVE_EXECUTE = 3;

    /** Debt of crossing the threshold with no depth at all. */
    public static final int SHALLOW_TRANSITION_PENALTY = 600;
    /** Debt reduction per HP of phase depth (each HP below half max). */
    public static final int PHASE_DEPTH_CREDIT_PER_HP = 20;

    private ChampProfile() {
    }

    @Override
    public int evaluate(SaveState searchRootState, SaveState currentState,
                        CombatFeatures features) {
        // searchRootState is intentionally unused: replan invariance.
        if (currentState.curMapNodeState == null
                || currentState.curMapNodeState.monsterData == null) {
            return 0;
        }
        for (MonsterState monster : currentState.curMapNodeState.monsterData) {
            if (monster instanceof ChampState && monster.currentHealth > 0) {
                return evaluate((ChampState) monster);
            }
        }
        return 0;
    }

    private static int evaluate(ChampState champ) {
        if (!champ.isThresholdReached()) {
            return 0;
        }
        byte nextMove = champ.getMoveInfo() == null ? 0 : champ.getMoveInfo().nextMove;
        if (nextMove != CHAMP_MOVE_ANGER) {
            // EXECUTE and later phase-2 turns: exact intent damage and threat
            // handle the risk; no permanent phase penalty.
            return 0;
        }

        int halfHp = champ.maxHealth / 2;
        int phaseDepth = Math.max(0, halfHp - champ.currentHealth);
        int debt = Math.max(0, SHALLOW_TRANSITION_PENALTY - phaseDepth * PHASE_DEPTH_CREDIT_PER_HP);
        return -debt;
    }
}

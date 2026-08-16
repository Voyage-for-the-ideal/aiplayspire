package battleaimod.evaluation.encounter;

import battleaimod.evaluation.CombatFeatures;
import savestate.SaveState;
import savestate.monsters.MonsterState;
import savestate.monsters.exordium.HexaghostState;

/**
 * Hexaghost: future cycle pressure.
 * <p>
 * The generic evaluator sees the CURRENT intent (incoming damage, strength,
 * burden) but cannot see how close the Inferno is.  This profile exposes only
 * that future pressure, based on the real encounter state machine (verified
 * against the game bytecode):
 * <pre>
 *   getMove() switch on orbActiveCount:
 *     0 -> SEAR, 1 -> TACKLE, 2 -> SEAR, 3 -> STRENGTHEN,
 *     4 -> TACKLE, 5 -> SEAR, 6 -> INFERNO (nextMove = 6)
 *   not activated -> ACTIVATE (nextMove = 5): takes a turn to activate all
 *     orbs (orbActiveCount = 6), then immediately performs the DIVIDER six
 *     hits (nextMove = 1) and deactivates back to 0.
 * </pre>
 * Rules:
 * <ul>
 *   <li>DIVIDER (nextMove 1) is NOT an Inferno deadline: orb 6 + DIVIDER gets
 *       no cycle penalty, even though the opening also drives orb count to 6.</li>
 *   <li>When INFERNO is the actual next move, the current multi-hit damage is
 *       already in ThreatEvaluator.immediateDamageOf - the profile adds nothing
 *       and lets immediate threat take over.</li>
 *   <li>After the first Inferno, burnUpgraded makes every Sear's Burn more
 *       dangerous: a small post-Inferno escalation penalty.</li>
 * </ul>
 * Only current-state data is used, so replans cannot change the score.
 */
public final class HexaghostProfile implements EncounterProfile {

    public static final HexaghostProfile INSTANCE = new HexaghostProfile();

    // Hexaghost nextMove constants (verified via bytecode)
    /** DIVIDER: the opening six-hit attack performed in the Activate turn. */
    public static final byte HEXAGHOST_MOVE_DIVIDER = 1;
    /** INFERNO: six-hit Burn attack at the end of each cycle. */
    public static final byte HEXAGHOST_MOVE_INFERNO = 6;

    /**
     * Cycle pressure by orbActiveCount: how much future pressure the current
     * cycle position carries.  Index 6 is unused (Inferno/Divider handled
     * explicitly); the values are deliberately small enough that a solid burst
     * of damage out-scales the pressure.
     */
    public static final int[] ORB_CYCLE_PRESSURE = {0, -40, -80, -140, -220, -320, 0};

    /** Penalty once the battle is past the first Inferno (upgraded Burns). */
    public static final int POST_INFERNO_ESCALATION_PENALTY = -150;

    private HexaghostProfile() {
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
            if (monster instanceof HexaghostState && monster.currentHealth > 0) {
                return evaluate((HexaghostState) monster);
            }
        }
        return 0;
    }

    private static int evaluate(HexaghostState hexaghost) {
        int score = 0;

        byte nextMove = hexaghost.getMoveInfo() == null
                ? 0 : hexaghost.getMoveInfo().nextMove;

        if (nextMove == HEXAGHOST_MOVE_INFERNO) {
            // Inferno's actual damage is already scored by ThreatEvaluator as
            // immediate threat; do not double count it here.
        } else if (nextMove != HEXAGHOST_MOVE_DIVIDER) {
            // Normal cycle (SEAR / TACKLE / STRENGTHEN): the closer the orb
            // counter is to Inferno, the higher the future pressure.
            int orb = Math.max(0, Math.min(6, hexaghost.getOrbActiveCount()));
            score += ORB_CYCLE_PRESSURE[orb];
        }
        // DIVIDER (nextMove 1): opening state with orb count 6 is NOT an
        // Inferno deadline, so no cycle penalty is applied.

        if (hexaghost.isBurnUpgraded()) {
            score += POST_INFERNO_ESCALATION_PENALTY;
        }
        return score;
    }
}

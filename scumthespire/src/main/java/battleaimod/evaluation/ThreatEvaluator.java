package battleaimod.evaluation;

import com.megacrit.cardcrawl.monsters.AbstractMonster;
import savestate.EnemyMoveInfoState;
import savestate.SaveState;
import savestate.monsters.MonsterState;

/**
 * Estimates how much danger an enemy poses to the player.
 * <p>
 * Threat is deliberately separate from killability: killing an enemy is valued by
 * the search as "threat removed" (before - after), so a high-damage attacker
 * naturally becomes the best kill target without hard-coded per-monster rules.
 */
public final class ThreatEvaluator {

    private ThreatEvaluator() {
    }

    private static boolean isAttackIntent(AbstractMonster.Intent intent) {
        return intent == AbstractMonster.Intent.ATTACK
                || intent == AbstractMonster.Intent.ATTACK_BUFF
                || intent == AbstractMonster.Intent.ATTACK_DEBUFF
                || intent == AbstractMonster.Intent.ATTACK_DEFEND;
    }

    /**
     * Effective incoming damage for the monster's current intent.
     * <p>
     * Prefers the game-computed {@code intentDmg} (already includes Strength,
     * Weak, Vulnerable-style adjustments made by the game itself); falls back
     * to baseDamage + Strength for legacy save states without intent_damage.
     * Multi-hit intents multiply the per-hit value by the hit count.
     */
    public static int immediateDamageOf(MonsterState monster) {
        EnemyMoveInfoState moveInfo = monster.getMoveInfo();
        if (moveInfo == null || !isAttackIntent(moveInfo.intent)) {
            return 0;
        }
        int perHit;
        if (monster.getIntentDamage() >= 0) {
            // Game-computed per-hit intent damage: never add Strength again.
            perHit = monster.getIntentDamage();
        } else {
            // Legacy SaveState fallback: reconstruct from the move info.
            int strength = CreaturePowerUtils.strengthOf(monster);
            perHit = Math.max(0, moveInfo.baseDamage + strength);
        }
        return moveInfo.isMultiDamage ? perHit * Math.max(1, moveInfo.multiplier) : perHit;
    }

    /** Number of hits the current attack intent will land (1 for non-multi attacks). */
    public static int incomingHitCountOf(MonsterState monster) {
        EnemyMoveInfoState moveInfo = monster.getMoveInfo();
        if (moveInfo == null || !isAttackIntent(moveInfo.intent)) {
            return 0;
        }
        return moveInfo.isMultiDamage ? Math.max(1, moveInfo.multiplier) : 1;
    }

    /**
     * Total threat of a single alive monster.
     * <p>
     * Strict threat semantics - danger the monster poses:
     * <ul>
     *   <li>immediateThreat - the damage about to hit the player</li>
     *   <li>scalingThreat - Strength that will keep adding damage in later turns</li>
     * </ul>
     * Block is deliberately NOT threat: a 20-block enemy that is not attacking
     * is not "20 points of incoming danger".  Block is killability / enemy
     * burden and is counted exactly once, in CombatFeatures.enemyBurdenDelta.
     */
    public static int threatOf(MonsterState monster) {
        if (monster.currentHealth <= 0) {
            return 0;
        }
        int immediate = immediateDamageOf(monster);
        int strength = CreaturePowerUtils.strengthOf(monster);
        int scaling = Math.max(0, strength) * TacticalEvaluator.SCALING_THREAT_WEIGHT;
        return immediate * TacticalEvaluator.IMMEDIATE_THREAT_WEIGHT + scaling;
    }

    /** Sum of threat over all alive monsters. */
    public static int totalThreat(SaveState state) {
        int total = 0;
        if (state.curMapNodeState == null || state.curMapNodeState.monsterData == null) {
            return 0;
        }
        for (MonsterState monster : state.curMapNodeState.monsterData) {
            total += threatOf(monster);
        }
        return total;
    }

    /** Threat of the single most dangerous alive monster (0 with no alive monsters). */
    public static int highestSingleThreat(SaveState state) {
        int highest = 0;
        if (state.curMapNodeState == null || state.curMapNodeState.monsterData == null) {
            return 0;
        }
        for (MonsterState monster : state.curMapNodeState.monsterData) {
            highest = Math.max(highest, threatOf(monster));
        }
        return highest;
    }
}

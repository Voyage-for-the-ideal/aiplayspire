package battleaimod.evaluation;

import com.megacrit.cardcrawl.monsters.AbstractMonster;
import savestate.EnemyMoveInfoState;
import savestate.SaveState;
import savestate.PlayerState;
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
     * Effective incoming damage for the monster's current intent, accounting for
     * its Strength and multi-hit multiplier. Zero for non-attack intents.
     */
    public static int immediateDamageOf(MonsterState monster) {
        return immediateDamageOf(monster, null);
    }

    /** Damage before block; follows vanilla float modifiers, then floors per hit. */
    public static int damagePerHit(MonsterState monster, PlayerState player) {
        EnemyMoveInfoState moveInfo = monster.getMoveInfo();
        if (monster.currentHealth <= 0 || moveInfo == null || !isAttackIntent(moveInfo.intent)) {
            return 0;
        }
        float damage = moveInfo.baseDamage + CreaturePowerUtils.strengthOf(monster);
        if (CreaturePowerUtils.powerAmount(monster, "Weakened") > 0) {
            damage *= hasRelic(player, "Paper Crane") ? 0.6f : 0.75f;
        }
        if (player != null) {
            if (CreaturePowerUtils.powerAmount(player, "Vulnerable") > 0) {
                damage *= hasRelic(player, "Odd Mushroom") ? 1.25f : 1.5f;
            }
            if ("Wrath".equals(player.getStanceId())) {
                damage *= 2f;
            }
            if (CreaturePowerUtils.powerAmount(monster, "BackAttack") > 0) {
                damage = (int) (damage * 1.5f);
            }
            if (CreaturePowerUtils.powerAmount(player, "IntangiblePlayer") > 0) {
                damage = Math.min(1f, damage);
            }
        }
        return Math.max(0, (int) Math.floor(damage));
    }

    public static int immediateDamageOf(MonsterState monster, PlayerState player) {
        return damagePerHit(monster, player) * incomingHitCountOf(monster);
    }

    public static boolean hasRelic(PlayerState player, String id) {
        if (player != null && player.relics != null) {
            for (savestate.relics.RelicState relic : player.relics) {
                if (id.equals(relic.relicId)) return true;
            }
        }
        return false;
    }

    /** Block is spent before Buffer; blocked hits do not consume Buffer stacks. */
    public static int incomingDamage(SaveState state) {
        int block = Math.max(0, state.playerState.currentBlock);
        int buffer = Math.max(0, CreaturePowerUtils.powerAmount(state.playerState, "Buffer"));
        int total = 0;
        for (MonsterState monster : state.curMapNodeState.monsterData) {
            int hit = damagePerHit(monster, state.playerState);
            int hits = incomingHitCountOf(monster);
            for (int i = 0; i < hits; i++) {
                int absorbed = Math.min(block, hit);
                block -= absorbed;
                int unblocked = hit - absorbed;
                if (unblocked > 0 && buffer > 0) buffer--;
                else total += unblocked;
            }
        }
        return total;
    }

    /** Number of hits the current attack intent will land (1 for non-multi attacks). */
    public static int incomingHitCountOf(MonsterState monster) {
        EnemyMoveInfoState moveInfo = monster.getMoveInfo();
        if (monster.currentHealth <= 0 || moveInfo == null || !isAttackIntent(moveInfo.intent)) {
            return 0;
        }
        return moveInfo.isMultiDamage ? Math.max(1, moveInfo.multiplier) : 1;
    }

    /**
     * Total threat of a single alive monster.
     * <p>
     * Components (Phase 1):
     * <ul>
     *   <li>immediateThreat - the damage about to hit the player</li>
     *   <li>scalingThreat - Strength that will keep adding damage in later turns</li>
     *   <li>blockThreat - block that must be cleared before the monster dies</li>
     * </ul>
     */
    public static int threatOf(MonsterState monster) {
        return threatOf(monster, null);
    }

    public static int threatOf(MonsterState monster, PlayerState player) {
        if (monster.currentHealth <= 0) {
            return 0;
        }
        int immediate = immediateDamageOf(monster, player);
        int strength = CreaturePowerUtils.strengthOf(monster);
        int scaling = Math.max(0, strength) * TacticalEvaluator.SCALING_THREAT_WEIGHT;
        int block = Math.max(0, monster.currentBlock) * TacticalEvaluator.BLOCK_THREAT_WEIGHT;
        return immediate * TacticalEvaluator.IMMEDIATE_THREAT_WEIGHT + scaling + block;
    }

    /** Sum of threat over all alive monsters. */
    public static int totalThreat(SaveState state) {
        int total = 0;
        int rawIncoming = 0;
        if (state.curMapNodeState == null || state.curMapNodeState.monsterData == null) {
            return 0;
        }
        for (MonsterState monster : state.curMapNodeState.monsterData) {
            total += threatOf(monster, state.playerState);
            rawIncoming += immediateDamageOf(monster, state.playerState);
        }
        return total + (incomingDamage(state) - rawIncoming) * TacticalEvaluator.IMMEDIATE_THREAT_WEIGHT;
    }

    /** Threat of the single most dangerous alive monster (0 with no alive monsters). */
    public static int highestSingleThreat(SaveState state) {
        int highest = 0;
        if (state.curMapNodeState == null || state.curMapNodeState.monsterData == null) {
            return 0;
        }
        for (MonsterState monster : state.curMapNodeState.monsterData) {
            highest = Math.max(highest, threatOf(monster, state.playerState));
        }
        return highest;
    }
}

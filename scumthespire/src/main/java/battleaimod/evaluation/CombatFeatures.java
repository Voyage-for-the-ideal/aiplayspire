package battleaimod.evaluation;

import savestate.PotionState;
import savestate.SaveState;
import savestate.monsters.MonsterState;

/**
 * Cheap, plain-data snapshot of a combat state, extracted directly from
 * {@link SaveState} without any JSON encode/decode.  Evaluators and profiles
 * read these features instead of poking at the state model.
 */
public final class CombatFeatures {

    // Player
    public int playerCurrentHp;
    public int playerMaxHp;
    public int playerBlock;
    public int hpLostFromCombatStart;
    public int playerStrength;
    public int playerDexterity;
    public int playerFocus;
    public int vulnerable;
    public int weak;
    public int frail;
    public int artifact;

    // Enemies
    public int aliveEnemyCount;
    public int deadEnemyCount;
    public int totalEnemyHp;
    public int totalEnemyBlock;
    public int totalEnemyEffectiveHp;
    public int enemyStrengthScaling;

    // Incoming damage this turn (block-adjusted)
    public int currentIncomingDamage;
    public int currentIncomingHitCount;
    public int highestEnemyIncomingDamage;

    // Threat (weighted, see ThreatEvaluator)
    public int aliveThreat;
    public int highestSingleEnemyThreat;
    public int nearLethalAttackingCount;

    // Progress
    public int damageDealtThisCombat;

    // Resources / misc
    public int potionValueRemaining;
    public int turnNumber;
    public int handSize;
    public int drawPileSize;
    public int discardPileSize;
    public boolean allEnemiesDead;

    private CombatFeatures() {
    }

    /**
     * Extracts features from a combat state.  {@code combatStartState} is the
     * state at combat start (may be null, in which case damage progress is 0);
     * {@code startingPlayerHealth} is the player HP at combat start.
     */
    public static CombatFeatures extract(SaveState currentState, SaveState combatStartState,
                                         int startingPlayerHealth) {
        CombatFeatures features = new CombatFeatures();
        if (currentState == null || currentState.playerState == null
                || currentState.curMapNodeState == null) {
            return features;
        }

        features.turnNumber = currentState.turn;
        features.playerCurrentHp = currentState.playerState.getCurrentHealth();
        features.playerMaxHp = currentState.playerState.maxHealth;
        features.playerBlock = Math.max(0, currentState.playerState.currentBlock);
        features.hpLostFromCombatStart = Math.max(0, startingPlayerHealth - features.playerCurrentHp);
        features.playerStrength = CreaturePowerUtils.playerStrength(currentState.playerState);
        features.playerDexterity = CreaturePowerUtils.playerDexterity(currentState.playerState);
        features.playerFocus = CreaturePowerUtils.playerFocus(currentState.playerState);
        features.vulnerable = CreaturePowerUtils.playerVulnerable(currentState.playerState);
        features.weak = CreaturePowerUtils.playerWeak(currentState.playerState);
        features.frail = CreaturePowerUtils.playerFrail(currentState.playerState);
        features.artifact = CreaturePowerUtils.playerArtifact(currentState.playerState);

        features.handSize = currentState.playerState.hand == null
                ? 0 : currentState.playerState.hand.length;
        features.drawPileSize = currentState.playerState.drawPile == null
                ? 0 : currentState.playerState.drawPile.length;
        features.discardPileSize = currentState.playerState.discardPile == null
                ? 0 : currentState.playerState.discardPile.length;

        features.potionValueRemaining = potionValue(currentState);

        if (currentState.curMapNodeState.monsterData == null) {
            features.allEnemiesDead = true;
            return features;
        }

        int incoming = 0;
        int incomingHits = 0;
        int highestIncoming = 0;
        int threat = 0;
        int highestThreat = 0;
        int nearLethalAttacking = 0;

        for (MonsterState monster : currentState.curMapNodeState.monsterData) {
            boolean alive = monster.currentHealth > 0;
            if (alive) {
                features.aliveEnemyCount++;
                features.totalEnemyHp += Math.max(0, monster.currentHealth);
                features.totalEnemyBlock += Math.max(0, monster.currentBlock);
                features.totalEnemyEffectiveHp +=
                        Math.max(0, monster.currentHealth) + Math.max(0, monster.currentBlock);

                int enemyStrength = CreaturePowerUtils.strengthOf(monster);
                features.enemyStrengthScaling += Math.max(0, enemyStrength);

                int monsterIncoming = ThreatEvaluator.immediateDamageOf(monster);
                incoming += monsterIncoming;
                incomingHits += ThreatEvaluator.incomingHitCountOf(monster);
                highestIncoming = Math.max(highestIncoming, monsterIncoming);

                int monsterThreat = ThreatEvaluator.threatOf(monster);
                threat += monsterThreat;
                highestThreat = Math.max(highestThreat, monsterThreat);

                if (monsterIncoming > 0 && monster.currentHealth <= TacticalEvaluator.NEAR_LETHAL_HP_THRESHOLD) {
                    nearLethalAttacking++;
                }
            } else {
                features.deadEnemyCount++;
            }
        }

        // Block absorbs the incoming damage that would land this turn
        features.currentIncomingDamage = Math.max(0, incoming - features.playerBlock);
        features.currentIncomingHitCount = incomingHits;
        features.highestEnemyIncomingDamage = highestIncoming;
        features.aliveThreat = threat;
        features.highestSingleEnemyThreat = highestThreat;
        features.nearLethalAttackingCount = nearLethalAttacking;
        features.allEnemiesDead = features.aliveEnemyCount == 0;

        features.damageDealtThisCombat = damageProgress(currentState, combatStartState);

        return features;
    }

    /**
     * Damage dealt to enemies this combat, capped per enemy at its starting
     * effective HP so overkill on an already-dead enemy adds nothing.
     */
    private static int damageProgress(SaveState currentState, SaveState combatStartState) {
        if (combatStartState == null || combatStartState.curMapNodeState == null
                || combatStartState.curMapNodeState.monsterData == null
                || currentState.curMapNodeState.monsterData == null) {
            return 0;
        }
        int progress = 0;
        int index = 0;
        for (MonsterState startMonster : combatStartState.curMapNodeState.monsterData) {
            if (index >= currentState.curMapNodeState.monsterData.size()) {
                break;
            }
            MonsterState currentMonster = currentState.curMapNodeState.monsterData.get(index++);
            if (startMonster.currentHealth <= 0) {
                continue;
            }
            int startEffective = startMonster.currentHealth + Math.max(0, startMonster.currentBlock);
            int currentEffective = currentMonster.currentHealth > 0
                    ? currentMonster.currentHealth + Math.max(0, currentMonster.currentBlock) : 0;
            progress += Math.min(Math.max(0, startEffective - currentEffective), startEffective);
        }
        return progress;
    }

    private static int potionValue(SaveState state) {
        int value = 0;
        if (state.playerState.potions == null) {
            return 0;
        }
        for (PotionState potion : state.playerState.potions) {
            if (potion.potionId == null || potion.potionId.equals("Potion Slot")
                    || !PotionState.POTION_VALUES.containsKey(potion.potionId)) {
                continue;
            }
            value += PotionState.POTION_VALUES.get(potion.potionId);
        }
        return value;
    }

    @Override
    public String toString() {
        return String.format(
                "hp:%d/%d(%d lost) block:%d str:%d dex:%d focus:%d | enemies:%d hp:%d block:%d effhp:%d incoming:%d(%d hits) threat:%d | progress:%d potions:%d turn:%d",
                playerCurrentHp, playerMaxHp, hpLostFromCombatStart, playerBlock, playerStrength,
                playerDexterity, playerFocus, aliveEnemyCount, totalEnemyHp, totalEnemyBlock,
                totalEnemyEffectiveHp, currentIncomingDamage, currentIncomingHitCount, aliveThreat,
                damageDealtThisCombat, potionValueRemaining, turnNumber);
    }
}

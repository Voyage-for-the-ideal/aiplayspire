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

    // Progress (roster-independent enemy burden reduction since the search root)
    public int enemyBurdenProgress;

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
     * Extracts features from a combat state.
     * <p>
     * {@code searchRootState} is the root state of the current search segment
     * (BattleAiController.startingState), NOT necessarily the combat-start
     * turn: after a replan the search can begin mid-combat.  It may be null,
     * in which case burden progress is 0.  {@code startingPlayerHealth} is the
     * player HP at that root.
     */
    public static CombatFeatures extract(SaveState currentState, SaveState searchRootState,
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

        features.enemyBurdenProgress = burdenProgress(currentState, searchRootState);

        return features;
    }

    /**
     * Roster-independent enemy burden progress since the search root:
     * <pre>
     *   rootBurden    = sum(alive enemy HP + block in the search root)
     *   currentBurden = sum(alive enemy HP + block now)
     *   progress      = clamp(rootBurden - currentBurden, 0, rootBurden)
     * </pre>
     * No index, no monster id, no fixed-roster assumption: summons raise the
     * current burden (progress can even drop across a Slime Boss split, which
     * is the correct signal that crossing the split line costs tempo), kills
     * remove burden, and overkill on a dead enemy adds nothing.
     */
    private static int burdenProgress(SaveState currentState, SaveState searchRootState) {
        if (searchRootState == null || searchRootState.curMapNodeState == null
                || searchRootState.curMapNodeState.monsterData == null
                || currentState.curMapNodeState == null
                || currentState.curMapNodeState.monsterData == null) {
            return 0;
        }
        int rootBurden = burden(searchRootState);
        int currentBurden = burden(currentState);
        return Math.max(0, Math.min(rootBurden - currentBurden, rootBurden));
    }

    /** Sum of alive enemy HP + block (enemy burden on the player). */
    private static int burden(SaveState state) {
        int burden = 0;
        for (MonsterState monster : state.curMapNodeState.monsterData) {
            if (monster.currentHealth > 0) {
                burden += Math.max(0, monster.currentHealth) + Math.max(0, monster.currentBlock);
            }
        }
        return burden;
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
                enemyBurdenProgress, potionValueRemaining, turnNumber);
    }
}

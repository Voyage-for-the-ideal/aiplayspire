package battleaimod.evaluation;

import battleaimod.BattleAiMod;
import battleaimod.ValueFunctions;
import battleaimod.battleai.BattleAiController;
import battleaimod.battleai.TurnNode;
import battleaimod.evaluation.encounter.EncounterRegistry;
import com.megacrit.cardcrawl.cards.colorless.RitualDagger;
import com.megacrit.cardcrawl.cards.green.Catalyst;
import com.megacrit.cardcrawl.cards.purple.ConjureBlade;
import com.megacrit.cardcrawl.cards.purple.LessonLearned;
import com.megacrit.cardcrawl.cards.red.Feed;
import com.megacrit.cardcrawl.cards.tempCards.Expunger;
import com.megacrit.cardcrawl.cards.tempCards.Miracle;
import com.megacrit.cardcrawl.relics.LizardTail;
import savestate.CardState;
import savestate.SaveState;
import savestate.StateFactories;
import savestate.relics.RelicState;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Combat evaluation built from reusable tactical signals instead of a flat
 * linear formula:
 * <pre>
 *   SaveState
 *      -&gt; CombatFeatures
 *      -&gt; survival (non-linear HP risk)
 *      + damage progress (capped, no overkill reward)
 *      + lethal / kill breakpoints
 *      - remaining enemy threat (kills remove threat naturally)
 *      + player scaling
 *      + retained resources
 *      + encounter adjustment (0 until Phase 2)
 *      = turn score
 * </pre>
 * <p>
 * All weights are named constants with clear semantics, gathered here so they
 * can be tuned in one place (Phase 5).  Evaluation is allocation-light: one
 * feature snapshot and one result object per call, no JSON.
 */
public final class TacticalEvaluator {

    // ------------------------------------------------------------------
    // Weights (centralized tuning)
    // ------------------------------------------------------------------

    /** Score for a dead player - so low that no living state can compete. */
    public static final int DEATH_PENALTY = -1_000_000;

    /** Per-HP survival value in the safe zone (see SurvivalEvaluator). */
    public static final int SAFE_HP_VALUE = 100;

    /** Score per point of damage progress against enemy effective HP. */
    public static final int DAMAGE_PROGRESS_WEIGHT = 10;

    /** Score per point of immediate enemy incoming damage in threat. */
    public static final int IMMEDIATE_THREAT_WEIGHT = 40;
    /** Score per enemy Strength point (future damage in later turns). */
    public static final int SCALING_THREAT_WEIGHT = 15;
    /** Score per enemy block point (must be cleared before the enemy dies). */
    public static final int BLOCK_THREAT_WEIGHT = 10;

    /** Bonus for finishing the whole battle. */
    public static final int BATTLE_COMPLETE_BONUS = 1_000_000;
    /**
     * An enemy at or below this HP, currently attacking, counts as near-lethal
     * (a CombatFeature for future command ordering; it carries no score).
     */
    public static final int NEAR_LETHAL_HP_THRESHOLD = 5;

    // ------------------------------------------------------------------
    // Observability
    // ------------------------------------------------------------------

    private static final String DEBUG_FLAG = "battleai.debugEvaluation";

    private TacticalEvaluator() {
    }

    /**
     * Evaluates the state at the start of {@code turnNode}'s turn and records
     * the evaluation against the owning controller's per-search metrics.
     */
    public static EvaluationResult evaluate(TurnNode turnNode) {
        BattleAiController controller = turnNode.controller;
        long startedAt = System.nanoTime();
        EvaluationResult result = evaluate(controller.startingState,
                turnNode.startingState.saveState, controller.startingHealth);
        controller.recordEvaluation(System.nanoTime() - startedAt);
        return result;
    }

    /**
     * Evaluates {@code currentState} given the state at combat start.
     * <p>
     * {@code combatStartState} may be null (damage progress will be 0); for real
     * search it is always {@code controller.startingState}.
     */
    public static EvaluationResult evaluate(SaveState combatStartState, SaveState currentState,
                                            int startingPlayerHealth) {
        CombatFeatures features = CombatFeatures.extract(currentState, combatStartState,
                startingPlayerHealth);

        EvaluationResult result = new EvaluationResult();

        // 1. Survival: non-linear HP risk (death is overwhelmingly bad)
        result.survivalScore = SurvivalEvaluator.survivalScore(features.playerCurrentHp,
                features.playerMaxHp, features.currentIncomingDamage);

        // 2. Damage progress: capped per enemy so overkill earns nothing
        result.damageProgressScore = features.damageDealtThisCombat * DAMAGE_PROGRESS_WEIGHT;

        // 3. Threat remaining: killing a dangerous attacker removes its threat,
        //    which is what makes "kill = block" fall out naturally.
        result.threatScore = -features.aliveThreat;

        // 4. Lethal: only true discrete states.  A dead enemy's value comes
        //    from threat removal (threatScore above); near-lethal enemies are
        //    exposed as a feature but carry no score.
        if (features.allEnemiesDead) {
            result.lethalScore += BATTLE_COMPLETE_BONUS;
        }

        // 5. Player scaling (powers: strength, dexterity, focus, ...)
        result.scalingScore = powerScore(currentState);

        // 6. Resources retained for the rest of the run
        result.resourceScore = resourceScore(currentState);

        // 7. Encounter-specific adjustment (only for mechanics that break the
        //    generic value relationships; 0 for most encounters)
        result.encounterScore = EncounterRegistry.resolve(currentState)
                .evaluate(combatStartState, currentState, features);

        // External extension hooks keep working exactly as before
        result.resourceScore += BattleAiMod.additionalValueFunctions.stream()
                .map(function -> function.apply(currentState))
                .collect(Collectors.summingInt(Integer::intValue));

        result.computeTotal();

        if (System.getProperty(DEBUG_FLAG) != null) {
            System.err.println("evaluation " + result + " | " + features);
        }
        return result;
    }

    /**
     * Score of the player's scaling powers, mirroring the legacy power score so
     * existing behavior is preserved.
     */
    private static int powerScore(SaveState state) {
        return state.playerState.powers.stream()
                .map(powerState -> ValueFunctions.POWER_VALUES.getOrDefault(powerState.powerId, 0)
                        * powerState.amount)
                .reduce(Integer::sum)
                .orElse(0);
    }

    /**
     * Value of retained resources: potions, gold, relics and long-term card
     * effects (Ritual Dagger, Feed, Lesson Learned, ...).  Mirrors the legacy
     * scoring so existing long-term signals are preserved.
     */
    private static int resourceScore(SaveState state) {
        int score = 0;

        int numOrbScore = state.playerState.maxOrbs == 0 ? -1000 : 0;
        score += numOrbScore;

        int numRitualDaggers = 0;
        int totalRitualDaggerDamage = 0;
        int numMiracles = 0;
        int numFeeds = 0;
        int numCatalysts = 0;
        int numLessonLearned = 0;
        int numConjures = 0;
        int conjureDamage = 0;

        for (CardState card : state.playerState.hand) {
            switch (StateFactories.cardIds[card.cardIdIndex]) {
                case RitualDagger.ID:
                    numRitualDaggers++;
                    totalRitualDaggerDamage += card.baseDamage;
                    break;
                case Miracle.ID:
                    numMiracles++;
                    break;
                case Feed.ID:
                    numFeeds++;
                    break;
                case ConjureBlade.ID:
                    numConjures++;
                    break;
                case Expunger.ID:
                    conjureDamage += card.baseMagicNumber;
                    break;
                case LessonLearned.ID:
                    numLessonLearned++;
                    break;
                case Catalyst.ID:
                    numCatalysts++;
                    break;
                default:
                    break;
            }
        }

        for (CardState card : state.playerState.drawPile) {
            switch (StateFactories.cardIds[card.cardIdIndex]) {
                case RitualDagger.ID:
                    numRitualDaggers++;
                    totalRitualDaggerDamage += card.baseDamage;
                    break;
                case Feed.ID:
                    numFeeds++;
                    break;
                case ConjureBlade.ID:
                    numConjures++;
                    break;
                case Expunger.ID:
                    conjureDamage += card.baseMagicNumber;
                    break;
                case LessonLearned.ID:
                    numLessonLearned++;
                    break;
                case Catalyst.ID:
                    numCatalysts++;
                    break;
                default:
                    break;
            }
        }

        for (CardState card : state.playerState.discardPile) {
            switch (StateFactories.cardIds[card.cardIdIndex]) {
                case RitualDagger.ID:
                    numRitualDaggers++;
                    totalRitualDaggerDamage += card.baseDamage;
                    break;
                case Feed.ID:
                    numFeeds++;
                    break;
                case ConjureBlade.ID:
                    numConjures++;
                    break;
                case Expunger.ID:
                    conjureDamage += card.baseMagicNumber;
                    break;
                case LessonLearned.ID:
                    numLessonLearned++;
                    break;
                case Catalyst.ID:
                    numCatalysts++;
                    break;
                default:
                    break;
            }
        }

        for (CardState card : state.playerState.exhaustPile) {
            if (StateFactories.cardIds[card.cardIdIndex].equals(RitualDagger.ID)) {
                totalRitualDaggerDamage += card.baseDamage;
            }
        }

        int miracleScore = numMiracles * 20;
        int ritualDaggerScore = numRitualDaggers * 40 + totalRitualDaggerDamage * 80;
        int feedScore = numFeeds * 40 + state.playerState.maxHealth * 30;
        int conjureBladeScore = numConjures * 25 + conjureDamage * 15;
        int lessonLearnedScore = numLessonLearned * 40 + state.lessonLearnedCount * 200;
        int parasiteScore = state.parasiteCount * -80;
        int catalystScore = numCatalysts * 25;

        score += miracleScore + ritualDaggerScore + feedScore + conjureBladeScore
                + lessonLearnedScore + parasiteScore + catalystScore;

        score += state.playerState.gold * 2;

        Optional<RelicState> lizardTail = state.playerState.relics.stream()
                .filter(relic -> relic.relicId.equals(LizardTail.ID) && relic.counter != -2)
                .findAny();
        if (lizardTail.isPresent()) {
            score += 400;
        }

        return score;
    }
}

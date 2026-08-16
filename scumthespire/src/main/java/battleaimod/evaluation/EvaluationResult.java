package battleaimod.evaluation;

/**
 * Interpretable breakdown of a tactical evaluation.
 * <p>
 * The total score is the sum of the components, so a debugger (or the
 * {@code battleai.debugEvaluation} flag) can explain why one state was
 * preferred over another.
 */
public class EvaluationResult {

    /** Overall score used to rank turn nodes. */
    public int totalScore;

    /** How safe the player's current HP situation is. Negative when close to death. */
    public int survivalScore;

    /** Reward for dealing damage / reducing enemy effective HP this combat. */
    public int damageProgressScore;

    /** Bonus for killing enemies, near-lethal threats and finishing the battle. */
    public int lethalScore;

    /** Penalty for the danger still alive on the board (remaining enemy threat). */
    public int threatScore;

    /** Value of player scaling (powers, strength, dexterity, focus, orbs). */
    public int scalingScore;

    /** Value of retained resources (potions, gold, relics, long-term card effects). */
    public int resourceScore;

    /** Encounter-specific adjustment. Zero for the generic evaluator (Phase 2+). */
    public int encounterScore;

    public void computeTotal() {
        totalScore = survivalScore + damageProgressScore + lethalScore + threatScore
                + scalingScore + resourceScore + encounterScore;
    }

    @Override
    public String toString() {
        return String.format("total=%d survival=%d damage=%d lethal=%d threat=%d scaling=%d resource=%d encounter=%d",
                totalScore, survivalScore, damageProgressScore, lethalScore, threatScore,
                scalingScore, resourceScore, encounterScore);
    }
}

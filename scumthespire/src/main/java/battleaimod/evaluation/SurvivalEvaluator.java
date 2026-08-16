package battleaimod.evaluation;

/**
 * Non-linear evaluation of the player's HP risk.
 * <p>
 * The value of one HP point is not constant: losing HP while near the danger
 * line is much more expensive than losing HP while healthy.  A piecewise
 * marginal-value model is used instead of {@code -damage * constant}:
 * <ul>
 *   <li>DEAD: a very large negative penalty, far below any living state</li>
 *   <li>CRITICAL band (hp &lt;= dangerLine): each HP worth CRITICAL_HP_VALUE</li>
 *   <li>DANGER band (dangerLine &lt; hp &lt;= 2*dangerLine): each HP worth DANGER_HP_VALUE</li>
 *   <li>SAFE band (hp &gt; 2*dangerLine): each HP worth SAFE_HP_VALUE</li>
 * </ul>
 * where dangerLine = max(incoming damage this turn, maxHp / 5).
 * <p>
 * The score is a base linear value minus band penalties, so incoming damage
 * raises the cost of low HP without inflating the absolute score of a healthy
 * state.  Keeping full HP is deliberately not an absolute goal: safe-band HP is
 * cheap enough that tempo/lethal plays can beat it.
 */
public final class SurvivalEvaluator {

    /** Per-HP marginal value while comfortably above the danger line. */
    public static final int SAFE_HP_VALUE = TacticalEvaluator.SAFE_HP_VALUE;
    /** Per-HP marginal value in the danger band (up to 2x the danger line). */
    public static final int DANGER_HP_VALUE = SAFE_HP_VALUE * 3;
    /** Per-HP marginal value at or below the danger line. */
    public static final int CRITICAL_HP_VALUE = SAFE_HP_VALUE * 10;

    public enum Status {
        DEAD, CRITICAL, DANGER, SAFE
    }

    private SurvivalEvaluator() {
    }

    /** The survival risk band the player currently sits in. */
    public static Status status(int currentHp, int maxHp, int effectiveIncomingDamage) {
        if (currentHp <= 0) {
            return Status.DEAD;
        }
        int dangerLine = dangerLine(maxHp, effectiveIncomingDamage);
        if (currentHp <= dangerLine) {
            return Status.CRITICAL;
        }
        if (currentHp <= 2 * dangerLine) {
            return Status.DANGER;
        }
        return Status.SAFE;
    }

    /**
     * Survival score for a combat's features.  Dead is overwhelmingly worse than
     * any living state; among living states, the marginal cost of lost HP grows
     * the closer the player is to the danger line.
     */
    public static int survivalScore(CombatFeatures features) {
        int score = survivalScore(features.playerCurrentHp, features.playerMaxHp,
                features.currentIncomingDamage);
        if (features.brawly) {
            // Brawly encounters (Gremlin Nob / Lagavulin) reward tempo over HP
            // preservation; Phase 2 replaces this with EncounterProfiles.
            score = (int) (score * TacticalEvaluator.BRAWL_SURVIVAL_MULTIPLIER);
        }
        return score;
    }

    public static int survivalScore(int currentHp, int maxHp, int effectiveIncomingDamage) {
        if (currentHp <= 0) {
            return TacticalEvaluator.DEATH_PENALTY;
        }
        int dangerLine = dangerLine(maxHp, effectiveIncomingDamage);

        // Base linear value, minus band penalties for being near the danger line.
        // Marginal value per HP: SAFE in the safe band, DANGER in the danger
        // band, CRITICAL at/below the danger line (piecewise continuous).
        int score = SAFE_HP_VALUE * currentHp;
        score -= (DANGER_HP_VALUE - SAFE_HP_VALUE) * Math.max(0, 2 * dangerLine - currentHp);
        score -= (CRITICAL_HP_VALUE - DANGER_HP_VALUE) * Math.max(0, dangerLine - currentHp);
        return score;
    }

    private static int dangerLine(int maxHp, int effectiveIncomingDamage) {
        return Math.max(effectiveIncomingDamage, Math.max(1, maxHp / 5));
    }
}

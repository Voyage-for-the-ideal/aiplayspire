package battleaimod.strategy;

/** Bounded tactical potential; no transient bonus can outweigh an actual terminal state. */
public final class MechanismScore {
    public static final int LIMIT = 20_000;
    private long total;
    private final StringBuilder rules = new StringBuilder();
    public void add(String rule, int value) {
        if (value == 0) return;
        total += value;
        if (rules.length() > 0) rules.append(", ");
        rules.append(rule).append('=').append(value);
    }
    public int total() { return (int) Math.max(-LIMIT, Math.min(LIMIT, total)); }
    public String rules() { return rules.toString(); }
}

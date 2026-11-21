package battleaimod.networking;

import com.megacrit.cardcrawl.helpers.SeedHelper;

/** Native planner deadline and optional reproducible game seed. */
public final class BackendEndpoint {
    private BackendEndpoint() { }

    public static long timeoutMillis(long defaultValue) {
        long value = Long.parseLong(System.getProperty("battleai.clientBudgetMs", Long.toString(defaultValue)));
        if (value < 1 || value > 90000) { throw new IllegalArgumentException("battleai.clientBudgetMs must be in 1..90000"); }
        return value;
    }

    public static Long runSeed() {
        String value = System.getProperty("battleai.runSeed");
        return value == null ? null : SeedHelper.getLong(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}

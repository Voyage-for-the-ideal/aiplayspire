package battleaimod.search;

import java.util.function.LongSupplier;

public final class SearchBudget {
    private final int maxExpansions;
    private final long timeoutNanos;
    private final LongSupplier nanoTime;
    private final long startedAt;

    public SearchBudget(int maxExpansions, long timeoutMillis) {
        this(maxExpansions, timeoutMillis, System::nanoTime);
    }

    SearchBudget(int maxExpansions, long timeoutMillis, LongSupplier nanoTime) {
        this.maxExpansions = Math.max(1, maxExpansions);
        this.timeoutNanos = Math.max(1L, timeoutMillis) * 1_000_000L;
        this.nanoTime = nanoTime;
        this.startedAt = nanoTime.getAsLong();
    }

    public boolean isExpansionLimitReached(long expandedNodes) {
        return expandedNodes >= maxExpansions;
    }

    public boolean isTimedOut() {
        return nanoTime.getAsLong() - startedAt >= timeoutNanos;
    }

    public long elapsedMillis() {
        return Math.max(0L, nanoTime.getAsLong() - startedAt) / 1_000_000L;
    }

    public int maxExpansions() {
        return maxExpansions;
    }

    public long timeoutMillis() {
        return timeoutNanos / 1_000_000L;
    }
}

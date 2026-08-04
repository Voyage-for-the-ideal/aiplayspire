package battleaimod.search;

public enum SearchProfile {
    FAST(5_000, 10_000L, true),
    BALANCED(15_000, 30_000L, true),
    DEEP(50_000, 90_000L, false);

    private final int maxExpansions;
    private final long timeoutMillis;
    private final boolean streamCommands;

    SearchProfile(int maxExpansions, long timeoutMillis, boolean streamCommands) {
        this.maxExpansions = maxExpansions;
        this.timeoutMillis = timeoutMillis;
        this.streamCommands = streamCommands;
    }

    public int maxExpansions() {
        return maxExpansions;
    }

    public long timeoutMillis() {
        return timeoutMillis;
    }

    public boolean streamCommands() {
        return streamCommands;
    }

    public static SearchProfile fromString(String value) {
        if (value == null) {
            return BALANCED;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return BALANCED;
        }
    }
}

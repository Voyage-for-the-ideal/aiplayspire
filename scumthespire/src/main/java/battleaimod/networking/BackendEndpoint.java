package battleaimod.networking;

import battleaimod.BattleAiMod;
import com.megacrit.cardcrawl.helpers.SeedHelper;

/** Client endpoint configuration is separate from battleai.port (the Java server's listener). */
public final class BackendEndpoint {
    private BackendEndpoint() { }

    public static boolean external() {
        String configured = System.getProperty("battleai.backend");
        if (configured == null && BattleAiMod.optionsConfig != null && BattleAiMod.optionsConfig.has("backend")) {
            configured = BattleAiMod.optionsConfig.getString("backend");
        }
        return "external".equalsIgnoreCase(configured);
    }

    public static int port() {
        String configured = System.getProperty("battleai.clientPort");
        if (configured == null && BattleAiMod.optionsConfig != null && BattleAiMod.optionsConfig.has("client_port")) {
            configured = BattleAiMod.optionsConfig.getString("client_port");
        }
        int port = Integer.parseInt(configured == null ? "5125" : configured);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("battleai.clientPort must be in 1..65535");
        }
        return port;
    }

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

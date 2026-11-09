package battleaimod.networking;

import com.google.gson.JsonObject;
import ludicrousspeed.LudicrousSpeedMod;
import ludicrousspeed.simulator.commands.Command;
import savestate.SaveState;
import java.util.function.Supplier;

/** Validate a native command against the live decision point before any game mutation. */
final class GuardedCommand implements Command {
    private final Command delegate;
    private final JsonObject expected;
    private final Supplier<JsonObject> current;
    private JsonObject expectedContext;

    GuardedCommand(Command delegate, JsonObject expected) {
        this(delegate, expected, () -> BattleStateProjection.fromSnapshot(new SaveState().jsonEncode()));
    }

    GuardedCommand(Command delegate, JsonObject expected, JsonObject context) {
        this(delegate, expected);
        this.expectedContext = context;
    }

    GuardedCommand(Command delegate, JsonObject expected, Supplier<JsonObject> current) {
        if (!expected.has("format") || expected.get("format").getAsInt() != 1) {
            throw new IllegalArgumentException("Unknown battle projection format");
        }
        this.delegate = delegate; this.expected = expected; this.current = current;
    }

    @Override public void execute() {
        try {
            if (!expected.equals(current.get()) || (expectedContext != null &&
                    !expectedContext.equals(BattleStateProjection.context(new SaveState().jsonEncode())))) {
                System.err.println("BATTLE_ENGINE_PRECONDITION_MISMATCH command=" + delegate.encode());
                LudicrousSpeedMod.mustRestart = true;
                return;
            }
        } catch (RuntimeException error) {
            System.err.println("BATTLE_ENGINE_PRECONDITION_ERROR " + error);
            LudicrousSpeedMod.mustRestart = true;
            return;
        }
        delegate.execute();
    }

    @Override public String encode() { return delegate.encode(); }
    @Override public String toString() { return delegate.toString(); }
}

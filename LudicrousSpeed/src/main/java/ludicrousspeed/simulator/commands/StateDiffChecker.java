package ludicrousspeed.simulator.commands;

import ludicrousspeed.LudicrousSpeedMod;
import savestate.SaveState;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

/**
 * Shared state-diff verification used by commands during replay. Compares a
 * fresh full-state snapshot against the state file the server recorded before
 * the command was issued; on mismatch the replay is aborted via mustRestart.
 */
public class StateDiffChecker {
    public static boolean check(String diffStateString, String commandDescription) {
        if (diffStateString == null) {
            return true;
        }

        try {
            String actualState = new SaveState().diffEncode();
            String expectedState = "";
            try (FileInputStream fis = new FileInputStream(diffStateString);
                 InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                 BufferedReader reader = new BufferedReader(isr)) {
                expectedState = reader.lines().collect(Collectors.joining());
            }

            if (!SaveState.diff(actualState, expectedState)) {
                logVerificationFailure(commandDescription, diffStateString, "state mismatch");
                LudicrousSpeedMod.mustRestart = true;
                return false;
            }

            return true;
        } catch (FileNotFoundException e) {
            // A missing state file means the recorded state cannot be verified;
            // treat it as a mismatch rather than silently passing
            logVerificationFailure(commandDescription, diffStateString, "state file missing");
            e.printStackTrace();
            LudicrousSpeedMod.mustRestart = true;
            return false;
        } catch (IOException e) {
            logVerificationFailure(commandDescription, diffStateString, "state file unreadable");
            e.printStackTrace();
            LudicrousSpeedMod.mustRestart = true;
            return false;
        }
    }

    private static void logVerificationFailure(String commandDescription, String statePath, String reason) {
        System.err.println("PANIC PANIC PANIC " + commandDescription + " reason=" + reason +
                " plan=" + planId(statePath) + " state=" + statePath);
    }

    private static String planId(String statePath) {
        Path path = Paths.get(statePath);
        Path parent = path.getParent();
        return parent == null ? "legacy" : parent.getFileName().toString();
    }
}

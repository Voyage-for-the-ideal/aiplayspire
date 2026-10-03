package savestate.actions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Static regression check for preserving optional zero-card exhaust selections.
 * ExhaustAction cannot be constructed in a plain Maven JVM because its static
 * initialization requires the running game's localization and dungeon UI.
 */
public final class ExhaustActionStateRegressionTest {
    private ExhaustActionStateRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        String basedir = System.getProperty("project.basedir");
        if (basedir == null || basedir.isEmpty()) {
            throw new AssertionError("project.basedir was not provided");
        }

        Path sourcePath = Paths.get(
                basedir,
                "src",
                "main",
                "java",
                "savestate",
                "actions",
                "ExhaustActionState.java"
        );
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String compact = source.replaceAll("\\s+", "");
        String restoredConstructor = "newExhaustAction("
                + "AbstractDungeon.player,"
                + "AbstractDungeon.player,"
                + "amount,"
                + "isRandom,"
                + "anyNumber,"
                + "canPickZero)";

        int constructorCalls = countOccurrences(compact, restoredConstructor);
        if (constructorCalls != 2) {
            throw new AssertionError(
                    "expected queued and current action restores to preserve canPickZero; found "
                            + constructorCalls
                            + " matching constructor calls"
            );
        }

        String legacyConstructor = "newExhaustAction("
                + "AbstractDungeon.player,"
                + "AbstractDungeon.player,"
                + "amount,"
                + "isRandom,"
                + "anyNumber)";
        if (compact.contains(legacyConstructor)) {
            throw new AssertionError("found a restore path that drops canPickZero");
        }
    }

    private static int countOccurrences(String text, String expected) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(expected, offset)) >= 0) {
            count++;
            offset += expected.length();
        }
        return count;
    }
}

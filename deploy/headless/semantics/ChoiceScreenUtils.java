package com.example.communicationmod;

/** Formatting-only shim for the unchanged event extractor; no UI/action code. */
public final class ChoiceScreenUtils {
    private ChoiceScreenUtils() {}
    public static String removeTextFormatting(String text) {
        return text.replaceAll("~|@(\\S+)~|@", "$1").replaceAll("#.|NL", "");
    }
}

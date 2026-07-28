package com.megacrit.cardcrawl.helpers;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.random.Random;

import java.math.BigInteger;

public class SeedHelper {
    private static final String CHARACTERS = "0123456789ABCDEFGHIJKLMNPQRSTUVWXYZ";
    public static String cachedSeed = null;

    public static final int SEED_DEFAULT_LENGTH = getString(Long.MIN_VALUE).length();

    public static void setSeed(String seedStr) {
        if (seedStr.isEmpty()) {
            Settings.seedSet = false;
            Settings.seed = null;
            Settings.specialSeed = null;
        } else {
            long seed = getLong(seedStr);
            Settings.seedSet = true;
            Settings.seed = Long.valueOf(seed);
            Settings.specialSeed = null;
            Settings.isDailyRun = false;
            cachedSeed = null;
        }
    }

    public static String getUserFacingSeedString() {
        if (Settings.seed != null) {
            if (cachedSeed == null) {
                cachedSeed = getString(Settings.seed.longValue());
            }
            return cachedSeed;
        }
        return "";
    }

    public static String getValidCharacter(String character, String textSoFar) {
        character = character.toUpperCase();
        if (character.equals("O")) {
            character = "0";
        }
        boolean isValid = "0123456789ABCDEFGHIJKLMNPQRSTUVWXYZ".contains(character);
        if (isValid) {
            return character;
        }
        return null;
    }

    public static String sterilizeString(String raw) {
        raw = raw.trim().toUpperCase();

        String pattern = "([A-Z]*[0-9]*)*";
        if (raw.matches("([A-Z]*[0-9]*)*")) {
            return raw.replace("O", "0");
        }
        return "";
    }

    public static String getString(long seed) {
        StringBuilder bldr = new StringBuilder();

        BigInteger leftover = new BigInteger(Long.toUnsignedString(seed));
        BigInteger charCount = BigInteger.valueOf("0123456789ABCDEFGHIJKLMNPQRSTUVWXYZ".length());

        while (!leftover.equals(BigInteger.ZERO)) {
            BigInteger remainder = leftover.remainder(charCount);
            leftover = leftover.divide(charCount);

            int charIndex = remainder.intValue();
            char c = "0123456789ABCDEFGHIJKLMNPQRSTUVWXYZ".charAt(charIndex);
            bldr.insert(0, c);
        }
        return bldr.toString();
    }

    public static long getLong(String seedStr) {
        long total = 0L;
        seedStr = seedStr.toUpperCase().replaceAll("O", "0");
        for (int i = 0; i < seedStr.length(); i++) {
            char toFind = seedStr.charAt(i);
            int remainder = "0123456789ABCDEFGHIJKLMNPQRSTUVWXYZ".indexOf(toFind);
            if (remainder == -1) {
                System.out.println("Character in seed is invalid: " + toFind);
            }

            total *= "0123456789ABCDEFGHIJKLMNPQRSTUVWXYZ".length();
            total += remainder;
        }
        return total;
    }

    public static long generateUnoffensiveSeed(Random rng) {
        String safeString = "fuck";

        while (BadWordChecker.containsBadWord(safeString) || TrialHelper.isTrialSeed(safeString)) {
            long possible = rng.randomLong();
            safeString = getString(possible);
        }
        return getLong(safeString);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\SeedHelper
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


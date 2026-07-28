package com.megacrit.cardcrawl.helpers;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.Keyword;
import com.megacrit.cardcrawl.localization.KeywordStrings;

import java.util.TreeMap;

public class GameDictionary {
    private static final KeywordStrings keywordStrings = CardCrawlGame.languagePack.getKeywordString("Game Dictionary");
    public static final String[] TEXT = keywordStrings.TEXT;
    public static final Keyword ARTIFACT = keywordStrings.ARTIFACT;
    public static final Keyword BLOCK = keywordStrings.BLOCK;
    public static final Keyword EVOKE = keywordStrings.EVOKE;
    public static final Keyword CONFUSED = keywordStrings.CONFUSED;
    public static final Keyword CHANNEL = keywordStrings.CHANNEL;
    public static final Keyword CURSE = keywordStrings.CURSE;
    public static final Keyword DARK = keywordStrings.DARK;
    public static final Keyword DEXTERITY = keywordStrings.DEXTERITY;
    public static final Keyword ETHEREAL = keywordStrings.ETHEREAL;
    public static final Keyword EXHAUST = keywordStrings.EXHAUST;
    public static final Keyword FRAIL = keywordStrings.FRAIL;
    public static final Keyword FROST = keywordStrings.FROST;
    public static final Keyword INNATE = keywordStrings.INNATE;
    public static final Keyword INTANGIBLE = keywordStrings.INTANGIBLE;
    public static final Keyword FOCUS = keywordStrings.FOCUS;
    public static final Keyword LIGHTNING = keywordStrings.LIGHTNING;
    public static final Keyword LOCKED = keywordStrings.LOCKED;
    public static final Keyword LOCK_ON = keywordStrings.LOCK_ON;
    public static final Keyword OPENER = keywordStrings.OPENER;
    public static final Keyword PLASMA = keywordStrings.PLASMA;
    public static final Keyword POISON = keywordStrings.POISON;
    public static final Keyword RETAIN = keywordStrings.RETAIN;
    public static final Keyword SHIV = keywordStrings.SHIV;
    public static final Keyword STATUS = keywordStrings.STATUS;
    public static final Keyword STRENGTH = keywordStrings.STRENGTH;
    public static final Keyword STRIKE = keywordStrings.STRIKE;
    public static final Keyword TRANSFORM = keywordStrings.TRANSFORM;
    public static final Keyword UNKNOWN = keywordStrings.UNKNOWN;
    public static final Keyword UNPLAYABLE = keywordStrings.UNPLAYABLE;
    public static final Keyword UPGRADE = keywordStrings.UPGRADE;
    public static final Keyword VIGOR = keywordStrings.VIGOR;
    public static final Keyword VOID = keywordStrings.VOID;
    public static final Keyword VULNERABLE = keywordStrings.VULNERABLE;
    public static final Keyword WEAK = keywordStrings.WEAK;
    public static final Keyword WOUND = keywordStrings.WOUND;
    public static final Keyword DAZED = keywordStrings.DAZED;
    public static final Keyword BURN = keywordStrings.BURN;
    public static final Keyword THORNS = keywordStrings.THORNS;
    public static final Keyword STANCE = keywordStrings.STANCE;
    public static final Keyword WRATH = keywordStrings.WRATH;
    public static final Keyword CALM = keywordStrings.CALM;
    public static final Keyword ENLIGHTENMENT = keywordStrings.DIVINITY;
    public static final Keyword SCRY = keywordStrings.SCRY;
    public static final Keyword PRAYER = keywordStrings.PRAYER;
    public static final Keyword REGEN = keywordStrings.REGEN;
    public static final Keyword RITUAL = keywordStrings.RITUAL;
    public static final Keyword FATAL = keywordStrings.FATAL;

    public static final TreeMap<String, String> keywords = new TreeMap<>();
    public static final TreeMap<String, String> parentWord = new TreeMap<>();

    public static void initialize() {
        keywords.put("[R]", TEXT[0]);
        keywords.put("[G]", TEXT[0]);
        keywords.put("[B]", TEXT[0]);
        keywords.put("[W]", TEXT[0]);
        keywords.put("[E]", TEXT[0]);

        createDictionaryEntry(ARTIFACT.NAMES, ARTIFACT.DESCRIPTION);
        createDictionaryEntry(BLOCK.NAMES, BLOCK.DESCRIPTION);
        createDictionaryEntry(BURN.NAMES, BURN.DESCRIPTION);
        createDictionaryEntry(CALM.NAMES, CALM.DESCRIPTION);
        createDictionaryEntry(CHANNEL.NAMES, CHANNEL.DESCRIPTION);
        createDictionaryEntry(CONFUSED.NAMES, CONFUSED.DESCRIPTION);
        createDictionaryEntry(CURSE.NAMES, CURSE.DESCRIPTION);
        createDictionaryEntry(DARK.NAMES, DARK.DESCRIPTION);
        createDictionaryEntry(DAZED.NAMES, DAZED.DESCRIPTION);
        createDictionaryEntry(DEXTERITY.NAMES, DEXTERITY.DESCRIPTION);
        createDictionaryEntry(ENLIGHTENMENT.NAMES, ENLIGHTENMENT.DESCRIPTION);
        createDictionaryEntry(ETHEREAL.NAMES, ETHEREAL.DESCRIPTION);
        createDictionaryEntry(EVOKE.NAMES, EVOKE.DESCRIPTION);
        createDictionaryEntry(EXHAUST.NAMES, EXHAUST.DESCRIPTION);
        createDictionaryEntry(FOCUS.NAMES, FOCUS.DESCRIPTION);
        createDictionaryEntry(FRAIL.NAMES, FRAIL.DESCRIPTION);
        createDictionaryEntry(FROST.NAMES, FROST.DESCRIPTION);
        createDictionaryEntry(INNATE.NAMES, INNATE.DESCRIPTION);
        createDictionaryEntry(INTANGIBLE.NAMES, INTANGIBLE.DESCRIPTION);
        createDictionaryEntry(LIGHTNING.NAMES, LIGHTNING.DESCRIPTION);
        createDictionaryEntry(LOCK_ON.NAMES, LOCK_ON.DESCRIPTION);
        createDictionaryEntry(LOCKED.NAMES, LOCKED.DESCRIPTION);
        createDictionaryEntry(OPENER.NAMES, OPENER.DESCRIPTION);
        createDictionaryEntry(PLASMA.NAMES, PLASMA.DESCRIPTION);
        createDictionaryEntry(POISON.NAMES, POISON.DESCRIPTION);
        createDictionaryEntry(PRAYER.NAMES, PRAYER.DESCRIPTION);
        createDictionaryEntry(RETAIN.NAMES, RETAIN.DESCRIPTION);
        createDictionaryEntry(SCRY.NAMES, SCRY.DESCRIPTION);
        createDictionaryEntry(SHIV.NAMES, SHIV.DESCRIPTION);
        createDictionaryEntry(STANCE.NAMES, STANCE.DESCRIPTION);
        createDictionaryEntry(STATUS.NAMES, STATUS.DESCRIPTION);
        createDictionaryEntry(STRENGTH.NAMES, STRENGTH.DESCRIPTION);
        createDictionaryEntry(STRIKE.NAMES, STRIKE.DESCRIPTION);
        createDictionaryEntry(THORNS.NAMES, THORNS.DESCRIPTION);
        createDictionaryEntry(TRANSFORM.NAMES, TRANSFORM.DESCRIPTION);
        createDictionaryEntry(UNKNOWN.NAMES, UNKNOWN.DESCRIPTION);
        createDictionaryEntry(UNPLAYABLE.NAMES, UNPLAYABLE.DESCRIPTION);
        createDictionaryEntry(UPGRADE.NAMES, UPGRADE.DESCRIPTION);
        createDictionaryEntry(VIGOR.NAMES, VIGOR.DESCRIPTION);
        createDictionaryEntry(VOID.NAMES, VOID.DESCRIPTION);
        createDictionaryEntry(VULNERABLE.NAMES, VULNERABLE.DESCRIPTION);
        createDictionaryEntry(WEAK.NAMES, WEAK.DESCRIPTION);
        createDictionaryEntry(WOUND.NAMES, WOUND.DESCRIPTION);
        createDictionaryEntry(WRATH.NAMES, WRATH.DESCRIPTION);
        createDictionaryEntry(REGEN.NAMES, REGEN.DESCRIPTION);
        createDictionaryEntry(RITUAL.NAMES, RITUAL.DESCRIPTION);
        createDictionaryEntry(FATAL.NAMES, FATAL.DESCRIPTION);
    }

    private static void createDictionaryEntry(String[] names, String desc) {
        for (String n : names) {
            keywords.put(n, desc);
            parentWord.put(n, names[0]);
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\
 * GameDictionary.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


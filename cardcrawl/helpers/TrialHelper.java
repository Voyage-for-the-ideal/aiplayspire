package com.megacrit.cardcrawl.helpers;

import com.megacrit.cardcrawl.trials.*;

import java.util.HashMap;

public class TrialHelper {
    private static HashMap<String, TRIAL> trialKeysMap;

    private enum TRIAL {
        RANDOM_MODS, NO_CARD_DROPS, UNCEASING_TOP, LOSE_MAX_HP, SNECKO, SLOW, FORMS, DRAFT, MEGA_DRAFT, ONE_HP, MORE_CARDS, CURSED;
    }

    private static void initialize() {
        if (trialKeysMap != null) {
            return;
        }
        trialKeysMap = new HashMap<>();

        trialKeysMap.put(formatKey("RandomMods"), TRIAL.RANDOM_MODS);
        trialKeysMap.put(formatKey("DailyMods"), TRIAL.RANDOM_MODS);
        trialKeysMap.put(formatKey("StarterDeck"), TRIAL.NO_CARD_DROPS);
        trialKeysMap.put(formatKey("Inception"), TRIAL.UNCEASING_TOP);
        trialKeysMap.put(formatKey("FadeAway"), TRIAL.LOSE_MAX_HP);
        trialKeysMap.put(formatKey("PraiseSnecko"), TRIAL.SNECKO);
        trialKeysMap.put(formatKey("YoureTooSlow"), TRIAL.SLOW);
        trialKeysMap.put(formatKey("MyTrueForm"), TRIAL.FORMS);
        trialKeysMap.put(formatKey("Draft"), TRIAL.DRAFT);
        trialKeysMap.put(formatKey("MegaDraft"), TRIAL.MEGA_DRAFT);
        trialKeysMap.put(formatKey("1HitWonder"), TRIAL.ONE_HP);
        trialKeysMap.put(formatKey("MoreCards"), TRIAL.MORE_CARDS);
        trialKeysMap.put(formatKey("Cursed"), TRIAL.CURSED);
    }

    private static String formatKey(String key) {
        return SeedHelper.sterilizeString(key);
    }

    public static boolean isTrialSeed(String seed) {
        initialize();
        return trialKeysMap.containsKey(seed);
    }

    public static AbstractTrial getTrialForSeed(String seed) {
        initialize();
        if (seed == null) {
            return null;
        }
        TRIAL picked = trialKeysMap.get(seed);
        if (picked == null) {
            return null;
        }

        switch (picked) {
            case RANDOM_MODS:
                return (AbstractTrial) new RandomModsTrial();
            case NO_CARD_DROPS:
                return (AbstractTrial) new StarterDeckTrial();
            case UNCEASING_TOP:
                return (AbstractTrial) new InceptionTrial();
            case LOSE_MAX_HP:
                return (AbstractTrial) new LoseMaxHpTrial();
            case SNECKO:
                return (AbstractTrial) new SneckoTrial();
            case SLOW:
                return (AbstractTrial) new SlowpokeTrial();
            case FORMS:
                return (AbstractTrial) new MyTrueFormTrial();
            case DRAFT:
                return (AbstractTrial) new DraftTrial();
            case MEGA_DRAFT:
                return (AbstractTrial) new AnyColorDraftTrial();
            case ONE_HP:
                return (AbstractTrial) new OneHpTrial();
            case MORE_CARDS:
                return (AbstractTrial) new HoarderTrial();
            case CURSED:
                return (AbstractTrial) new CursedTrial();
        }
        return null;
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\
 * TrialHelper.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


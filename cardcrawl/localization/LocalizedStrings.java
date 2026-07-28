package com.megacrit.cardcrawl.localization;

import com.badlogic.gdx.Gdx;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.megacrit.cardcrawl.core.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class LocalizedStrings {
    private static final Logger logger = LogManager.getLogger(LocalizedStrings.class.getName());
    private static final String LOCALIZATION_DIR = "localization";
    public static String PERIOD = null;
    private static Map<String, MonsterStrings> monsters;
    private static Map<String, PowerStrings> powers;
    private static Map<String, CardStrings> cards;
    private static Map<String, RelicStrings> relics;
    private static Map<String, EventStrings> events;
    private static Map<String, PotionStrings> potions;
    private static Map<String, CreditStrings> credits;
    private static Map<String, TutorialStrings> tutorials;
    private static Map<String, KeywordStrings> keywords;
    private static Map<String, ScoreBonusStrings> scoreBonuses;
    private static Map<String, CharacterStrings> characters;
    private static Map<String, UIStrings> ui;
    private static Map<String, OrbStrings> orb;
    private static Map<String, StanceStrings> stance;
    public static Map<String, RunModStrings> mod;
    private static Map<String, BlightStrings> blights;
    private static Map<String, AchievementStrings> achievements;
    public static String break_chars = null;

    public LocalizedStrings() {
        String langPackDir;
        long startTime = System.currentTimeMillis();

        Gson gson = new Gson();

        switch (Settings.language) {
            case ENG:
                langPackDir = "localization" + File.separator + "eng";
                break;
            case DUT:
                langPackDir = "localization" + File.separator + "dut";
                break;
            case EPO:
                langPackDir = "localization" + File.separator + "epo";
                break;
            case PTB:
                langPackDir = "localization" + File.separator + "ptb";
                break;
            case ZHS:
                langPackDir = "localization" + File.separator + "zhs";
                break;
            case ZHT:
                langPackDir = "localization" + File.separator + "zht";
                break;
            case FIN:
                langPackDir = "localization" + File.separator + "fin";
                break;
            case FRA:
                langPackDir = "localization" + File.separator + "fra";
                break;
            case DEU:
                langPackDir = "localization" + File.separator + "deu";
                break;
            case GRE:
                langPackDir = "localization" + File.separator + "gre";
                break;
            case IND:
                langPackDir = "localization" + File.separator + "ind";
                break;
            case ITA:
                langPackDir = "localization" + File.separator + "ita";
                break;
            case JPN:
                if (Settings.isConsoleBuild) {
                    langPackDir = "localization" + File.separator + "jpn";
                    break;
                }
                langPackDir = "localization" + File.separator + "jpn2";
                break;

            case KOR:
                langPackDir = "localization" + File.separator + "kor";
                break;
            case NOR:
                langPackDir = "localization" + File.separator + "nor";
                break;
            case POL:
                langPackDir = "localization" + File.separator + "pol";
                break;
            case RUS:
                langPackDir = "localization" + File.separator + "rus";
                break;
            case SPA:
                langPackDir = "localization" + File.separator + "spa";
                break;
            case SRP:
                langPackDir = "localization" + File.separator + "srp";
                break;
            case SRB:
                langPackDir = "localization" + File.separator + "srb";
                break;
            case THA:
                langPackDir = "localization" + File.separator + "tha";
                break;
            case TUR:
                langPackDir = "localization" + File.separator + "tur";
                break;
            case UKR:
                langPackDir = "localization" + File.separator + "ukr";
                break;
            case VIE:
                langPackDir = "localization" + File.separator + "vie";
                break;
            case WWW:
                langPackDir = "localization" + File.separator + "www";
                break;
            default:
                langPackDir = "localization" + File.separator + "www";
                break;
        }
        String monsterPath = langPackDir + File.separator + "monsters.json";

        Type monstersType = (new TypeToken<Map<String, MonsterStrings>>() {
        }).getType();
        monsters = (Map<String, MonsterStrings>) gson.fromJson(loadJson(monsterPath), monstersType);

        String powerPath = langPackDir + File.separator + "powers.json";

        Type powerType = (new TypeToken<Map<String, PowerStrings>>() {
        }).getType();
        powers = (Map<String, PowerStrings>) gson.fromJson(loadJson(powerPath), powerType);

        String cardPath = langPackDir + File.separator + "cards.json";

        Type cardType = (new TypeToken<Map<String, CardStrings>>() {
        }).getType();
        cards = (Map<String, CardStrings>) gson.fromJson(loadJson(cardPath), cardType);

        String relicPath = langPackDir + File.separator + "relics.json";

        Type relicType = (new TypeToken<Map<String, RelicStrings>>() {
        }).getType();
        relics = (Map<String, RelicStrings>) gson.fromJson(loadJson(relicPath), relicType);

        String eventPath = langPackDir + File.separator + "events.json";

        Type eventType = (new TypeToken<Map<String, EventStrings>>() {
        }).getType();
        events = (Map<String, EventStrings>) gson.fromJson(loadJson(eventPath), eventType);

        String potionPath = langPackDir + File.separator + "potions.json";

        Type potionType = (new TypeToken<Map<String, PotionStrings>>() {
        }).getType();
        potions = (Map<String, PotionStrings>) gson.fromJson(loadJson(potionPath), potionType);

        String creditPath = langPackDir + File.separator + "credits.json";

        Type creditType = (new TypeToken<Map<String, CreditStrings>>() {
        }).getType();
        credits = (Map<String, CreditStrings>) gson.fromJson(loadJson(creditPath), creditType);

        String tutorialsPath = langPackDir + File.separator + "tutorials.json";

        Type tutorialType = (new TypeToken<Map<String, TutorialStrings>>() {
        }).getType();
        tutorials = (Map<String, TutorialStrings>) gson.fromJson(loadJson(tutorialsPath), tutorialType);

        String keywordsPath = langPackDir + File.separator + "keywords.json";

        Type keywordsType = (new TypeToken<Map<String, KeywordStrings>>() {
        }).getType();
        keywords = (Map<String, KeywordStrings>) gson.fromJson(loadJson(keywordsPath), keywordsType);

        String scoreBonusesPath = langPackDir + File.separator + "score_bonuses.json";

        Type scoreBonusType = (new TypeToken<Map<String, ScoreBonusStrings>>() {
        }).getType();
        scoreBonuses = (Map<String, ScoreBonusStrings>) gson.fromJson(loadJson(scoreBonusesPath), scoreBonusType);

        String characterPath = langPackDir + File.separator + "characters.json";

        Type characterType = (new TypeToken<Map<String, CharacterStrings>>() {
        }).getType();
        characters = (Map<String, CharacterStrings>) gson.fromJson(loadJson(characterPath), characterType);

        String uiPath = langPackDir + File.separator + "ui.json";

        Type uiType = (new TypeToken<Map<String, UIStrings>>() {
        }).getType();
        ui = (Map<String, UIStrings>) gson.fromJson(loadJson(uiPath), uiType);
        PERIOD = (getUIString("Period")).TEXT[0];

        String orbPath = langPackDir + File.separator + "orbs.json";

        Type orbType = (new TypeToken<Map<String, OrbStrings>>() {
        }).getType();
        orb = (Map<String, OrbStrings>) gson.fromJson(loadJson(orbPath), orbType);

        String stancePath = langPackDir + File.separator + "stances.json";

        Type stanceType = (new TypeToken<Map<String, StanceStrings>>() {
        }).getType();
        stance = (Map<String, StanceStrings>) gson.fromJson(loadJson(stancePath), stanceType);

        String modPath = langPackDir + File.separator + "run_mods.json";

        Type modType = (new TypeToken<Map<String, RunModStrings>>() {
        }).getType();
        mod = (Map<String, RunModStrings>) gson.fromJson(loadJson(modPath), modType);

        String blightPath = langPackDir + File.separator + "blights.json";

        Type blightType = (new TypeToken<Map<String, BlightStrings>>() {
        }).getType();
        blights = (Map<String, BlightStrings>) gson.fromJson(loadJson(blightPath), blightType);

        String achievePath = langPackDir + File.separator + "achievements.json";

        Type achieveType = (new TypeToken<Map<String, AchievementStrings>>() {
        }).getType();
        achievements = (Map<String, AchievementStrings>) gson.fromJson(loadJson(achievePath), achieveType);

        String lineBreakPath = langPackDir + File.separator + "line_break.json";
        if (Gdx.files.internal(lineBreakPath).exists()) {
            break_chars = Gdx.files.internal(lineBreakPath).readString(String.valueOf(StandardCharsets.UTF_8));
        }

        logger.info("Loc Strings load time: " + (System.currentTimeMillis() - startTime) + "ms");
    }

    public PowerStrings getPowerStrings(String powerName) {
        if (powers.containsKey(powerName)) {
            return powers.get(powerName);
        }
        logger.info("[ERROR] PowerString: " + powerName + " not found");
        return PowerStrings.getMockPowerString();
    }

    public MonsterStrings getMonsterStrings(String monsterName) {
        if (monsters.containsKey(monsterName)) {
            return monsters.get(monsterName);
        }
        logger.info("[ERROR] MonsterString: " + monsterName + " not found");
        return MonsterStrings.getMockMonsterString();
    }

    public EventStrings getEventString(String eventName) {
        if (events.containsKey(eventName)) {
            return events.get(eventName);
        }
        logger.info("[ERROR] EventString: " + eventName + " not found");
        return EventStrings.getMockEventString();
    }

    public PotionStrings getPotionString(String potionName) {
        if (potions.containsKey(potionName)) {
            return potions.get(potionName);
        }
        logger.info("[ERROR] PotionString: " + potionName + " not found");
        return PotionStrings.getMockPotionString();
    }

    public CreditStrings getCreditString(String creditName) {
        if (credits.containsKey(creditName)) {
            return credits.get(creditName);
        }
        logger.info("[ERROR] CreditString: " + creditName + " not found");
        return CreditStrings.getMockCreditString();
    }

    public TutorialStrings getTutorialString(String tutorialName) {
        if (tutorials.containsKey(tutorialName)) {
            return tutorials.get(tutorialName);
        }
        logger.info("[ERROR] TutorialString: " + tutorialName + " not found");
        return TutorialStrings.getMockTutorialString();
    }

    public KeywordStrings getKeywordString(String keywordName) {
        return keywords.get(keywordName);
    }

    public CharacterStrings getCharacterString(String characterName) {
        return characters.get(characterName);
    }

    public UIStrings getUIString(String uiName) {
        return ui.get(uiName);
    }

    public OrbStrings getOrbString(String orbName) {
        if (orb.containsKey(orbName)) {
            return orb.get(orbName);
        }
        logger.info("[ERROR] OrbStrings: " + orbName + " not found");
        return OrbStrings.getMockOrbString();
    }

    public StanceStrings getStanceString(String stanceName) {
        return stance.get(stanceName);
    }

    public RunModStrings getRunModString(String modName) {
        if (mod.containsKey(modName)) {
            return mod.get(modName);
        }
        logger.info("[ERROR] RunModStrings: " + modName + " not found");
        return RunModStrings.getMockModString();
    }

    public BlightStrings getBlightString(String blightName) {
        if (blights.containsKey(blightName)) {
            return blights.get(blightName);
        }
        logger.info("[ERROR] BlightStrings: " + blightName + " not found");
        return BlightStrings.getBlightOrbString();
    }

    public ScoreBonusStrings getScoreString(String scoreName) {
        if (scoreBonuses.containsKey(scoreName)) {
            return scoreBonuses.get(scoreName);
        }
        logger.info("[ERROR] ScoreBonusStrings: " + scoreName + " not found");
        return ScoreBonusStrings.getScoreBonusString();
    }

    public AchievementStrings getAchievementString(String achievementName) {
        return achievements.get(achievementName);
    }

    public CardStrings getCardStrings(String cardName) {
        if (cards.containsKey(cardName)) {
            return cards.get(cardName);
        }
        logger.info("[ERROR] CardString: " + cardName + " not found");
        return CardStrings.getMockCardString();
    }

    public static String[] createMockStringArray(int size) {
        String[] retVal = new String[size];
        for (int i = 0; i < retVal.length; i++) {
            retVal[i] = "[MISSING_" + i + "]";
        }
        return retVal;
    }

    public RelicStrings getRelicStrings(String relicName) {
        return relics.get(relicName);
    }

    private static String loadJson(String jsonPath) {
        return Gdx.files.internal(jsonPath).readString(String.valueOf(StandardCharsets.UTF_8));
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\localization\
 * LocalizedStrings.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


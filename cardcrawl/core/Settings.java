package com.megacrit.cardcrawl.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.helpers.Prefs;
import com.megacrit.cardcrawl.helpers.SaveHelper;
import com.megacrit.cardcrawl.screens.DisplayOption;
import com.megacrit.cardcrawl.screens.custom.CustomModeScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class Settings {
    private static final Logger logger = LogManager.getLogger(Settings.class.getName());

    public static boolean isDev = false;

    public static boolean isBeta = false;

    public static boolean isAlpha = false;

    public static boolean isModded = false;

    public static boolean isControllerMode = false;

    public static boolean isMobile = false;

    public static boolean testFonts = false;

    public static boolean isDebug = false;

    public static boolean isInfo = false;

    public static boolean isTestingNeow = false;

    public static boolean usesTrophies = false;

    public static boolean isConsoleBuild = false;
    public static boolean usesProfileSaves = false;
    public static boolean isTouchScreen = false;
    public static boolean isDemo = false;
    public static boolean isShowBuild = false;
    public static boolean isPublisherBuild = false;
    public static GameLanguage language;
    public static boolean lineBreakViaCharacter = false;
    public static boolean usesOrdinal = true;
    public static boolean leftAlignCards = false;
    public static boolean manualLineBreak = false;
    public static boolean removeAtoZSort = false;
    public static boolean manualAndAutoLineBreak = false;
    public static Prefs soundPref;
    public static Prefs dailyPref;
    public static Prefs gamePref;
    public static boolean isDailyRun;
    public static boolean hasDoneDailyToday;
    public static long dailyDate = 0L;
    public static long totalPlayTime;
    public static boolean isFinalActAvailable;
    public static boolean hasRubyKey;
    public static boolean hasEmeraldKey;
    public static boolean hasSapphireKey;
    public static boolean isEndless;
    public static boolean isTrial;
    public static Long specialSeed;
    public static String trialName;
    public static boolean IS_FULLSCREEN;
    public static boolean IS_W_FULLSCREEN;
    public static boolean IS_V_SYNC;
    public static int MAX_FPS;
    public static int M_W;
    public static int M_H;
    public static int SAVED_WIDTH;
    public static int SAVED_HEIGHT;
    public static int WIDTH;
    public static int HEIGHT;
    public static boolean isSixteenByTen = false, isFourByThree = false, isTwoSixteen = false;
    public static boolean isLetterbox = false;
    public static int HORIZ_LETTERBOX_AMT = 0;
    public static int VERT_LETTERBOX_AMT = 0;
    public static ArrayList<DisplayOption> displayOptions = null;
    public static int displayIndex = 0;

    public static float scale;

    public static float renderScale;
    public static float xScale;
    public static float yScale;
    public static float FOUR_BY_THREE_OFFSET_Y;
    public static float LETTERBOX_OFFSET_Y;
    public static Long seed;
    public static boolean seedSet = false;
    public static long seedSourceTimestamp;
    public static boolean isBackgrounded = false;
    public static float bgVolume = 0.0F;

    public static final String MASTER_VOLUME_PREF = "Master Volume";

    public static final String MUSIC_VOLUME_PREF = "Music Volume";

    public static final String SOUND_VOLUME_PREF = "Sound Volume";

    public static final String AMBIENCE_ON_PREF = "Ambience On";

    public static final String MUTE_IF_BG_PREF = "Mute in Bg";

    public static final float DEFAULT_MASTER_VOLUME = 0.5F;

    public static final float DEFAULT_MUSIC_VOLUME = 0.5F;

    public static final float DEFAULT_SOUND_VOLUME = 0.5F;

    public static float MASTER_VOLUME;

    public static float MUSIC_VOLUME;

    public static float SOUND_VOLUME;

    public static boolean AMBIANCE_ON;
    public static final String SCREEN_SHAKE_PREF = "Screen Shake";
    public static final String SUM_DMG_PREF = "Summed Damage";
    public static final String BLOCKED_DMG_PREF = "Blocked Damage";
    public static final String HAND_CONF_PREF = "Hand Confirmation";
    public static final String EFFECTS_PREF = "Particle Effects";
    public static final String FAST_MODE_PREF = "Fast Mode";
    public static final String UPLOAD_PREF = "Upload Data";
    public static final Color CREAM_COLOR = new Color(-597249);
    public static final String PLAYTESTER_ART = "Playtester Art";
    public static final String SHOW_CARD_HOTKEYS_PREF = "Show Card keys";
    public static final String BIG_TEXT_PREF = "Bigger Text";
    public static final String LONG_PRESS_PREF = "Long-press Enabled";
    public static final String CONTROLLER_ENABLED_PREF = "Controller Enabled";
    public static final String TOUCHSCREEN_ENABLED_PREF = "Touchscreen Enabled";
    public static final String LAST_DAILY = "LAST_DAILY";
    public static boolean SHOW_DMG_SUM;
    public static boolean SHOW_DMG_BLOCK;
    public static boolean FAST_HAND_CONF;
    public static boolean FAST_MODE;
    public static boolean CONTROLLER_ENABLED;
    public static boolean TOUCHSCREEN_ENABLED;
    public static boolean DISABLE_EFFECTS;
    public static boolean UPLOAD_DATA;
    public static boolean SCREEN_SHAKE;
    public static boolean PLAYTESTER_ART_MODE;
    public static boolean SHOW_CARD_HOTKEYS;
    public static boolean USE_LONG_PRESS = false;
    public static boolean BIG_TEXT_MODE = false;
    public static final Color LIGHT_YELLOW_COLOR = new Color(-1202177);
    public static final Color RED_TEXT_COLOR = new Color(-10132481);
    public static final Color GREEN_TEXT_COLOR = new Color(2147418367);
    public static final Color BLUE_TEXT_COLOR = new Color(-2016482305);
    public static final Color GOLD_COLOR = new Color(-272084481);
    public static final Color PURPLE_COLOR = new Color(-293409025);
    public static final Color TOP_PANEL_SHADOW_COLOR = new Color(64);
    public static final Color HALF_TRANSPARENT_WHITE_COLOR = new Color(1.0F, 1.0F, 1.0F, 0.5F);
    public static final Color QUARTER_TRANSPARENT_WHITE_COLOR = new Color(1.0F, 1.0F, 1.0F, 0.25F);
    public static final Color TWO_THIRDS_TRANSPARENT_BLACK_COLOR = new Color(0.0F, 0.0F, 0.0F, 0.66F);
    public static final Color HALF_TRANSPARENT_BLACK_COLOR = new Color(0.0F, 0.0F, 0.0F, 0.5F);
    public static final Color QUARTER_TRANSPARENT_BLACK_COLOR = new Color(0.0F, 0.0F, 0.0F, 0.25F);
    public static final Color RED_RELIC_COLOR = new Color(-10132545);
    public static final Color GREEN_RELIC_COLOR = new Color(2147418303);
    public static final Color BLUE_RELIC_COLOR = new Color(-2016482369);
    public static final Color PURPLE_RELIC_COLOR = new Color(-935526465);

    public static final float POST_ATTACK_WAIT_DUR = 0.1F;

    public static final float WAIT_BEFORE_BATTLE_TIME = 1.0F;
    public static float ACTION_DUR_XFAST = 0.1F;
    public static float ACTION_DUR_FASTER = 0.2F;
    public static float ACTION_DUR_FAST = 0.25F;
    public static float ACTION_DUR_MED = 0.5F;
    public static float ACTION_DUR_LONG = 1.0F;
    public static float ACTION_DUR_XLONG = 1.5F;
    public static float CARD_DROP_END_Y;
    public static float SCROLL_SPEED;
    public static float MAP_SCROLL_SPEED;
    public static final float SCROLL_LERP_SPEED = 12.0F;
    public static final float SCROLL_SNAP_BACK_SPEED = 10.0F;
    public static float DEFAULT_SCROLL_LIMIT;
    public static float MAP_DST_Y;
    public static final float CLICK_SPEED_THRESHOLD = 0.4F;
    public static float CLICK_DIST_THRESHOLD;
    public static float POTION_W;
    public static float POTION_Y;
    public static final Color DISCARD_COLOR = Color.valueOf("8a769bff");
    public static final Color DISCARD_GLOW_COLOR = Color.valueOf("553a66ff");
    public static final Color SHADOW_COLOR = new Color(0.0F, 0.0F, 0.0F, 0.5F);

    public static final float CARD_SOUL_SCALE = 0.12F;

    public static final float CARD_LERP_SPEED = 6.0F;

    public static float CARD_SNAP_THRESHOLD;

    public static float UI_SNAP_THRESHOLD;

    public static final float CARD_SCALE_LERP_SPEED = 7.5F;

    public static final float CARD_SCALE_SNAP_THRESHOLD = 0.003F;

    public static final float UI_LERP_SPEED = 9.0F;

    public static final float ORB_LERP_SPEED = 6.0F;

    public static final float MOUSE_LERP_SPEED = 20.0F;

    public static final float POP_LERP_SPEED = 8.0F;
    public static final float FADE_LERP_SPEED = 12.0F;
    public static final float SLOW_COLOR_LERP_SPEED = 3.0F;
    public static final float FADE_SNAP_THRESHOLD = 0.01F;
    public static final float ROTATE_LERP_SPEED = 12.0F;
    public static final float SCALE_SNAP_THRESHOLD = 0.003F;
    public static float HOVER_BUTTON_RISE_AMOUNT;
    public static final float CARD_VIEW_SCALE = 0.75F;
    public static float CARD_VIEW_PAD_X;
    public static float CARD_VIEW_PAD_Y;
    public static float OPTION_Y;
    public static float EVENT_Y;
    public static final int MAX_ASCENSION_LEVEL = 20;
    public static final float POST_COMBAT_WAIT_TIME = 0.25F;
    public static final int MAX_HAND_SIZE = 10;
    public static final int NUM_POTIONS = 3;
    public static final int NORMAL_POTION_DROP_RATE = 40;
    public static final int ELITE_POTION_DROP_RATE = 40;
    public static final int BOSS_GOLD_AMT = 100;
    public static final int BOSS_GOLD_JITTER = 5;
    public static final int ACHIEVEMENT_COUNT = 46;
    public static final int NORMAL_RARE_DROP_RATE = 3;
    public static final int NORMAL_UNCOMMON_DROP_RATE = 40;
    public static final int ELITE_RARE_DROP_RATE = 10;
    public static final int ELITE_UNCOMMON_DROP_RATE = 50;
    public static final int UNLOCK_PER_CHAR_COUNT = 5;
    public static boolean hideTopBar = false;
    public static boolean hidePopupDetails = false;
    public static boolean hideRelics = false;
    public static boolean hideLowerElements = false;
    public static boolean hideCards = false;
    public static boolean hideEndTurn = false;
    public static boolean hideCombatElements = false;
    public static final String SENDTODEVS = "sendToDevs";

    public enum GameLanguage {
        ENG, DUT, EPO, PTB, ZHS, ZHT, FIN, FRA, DEU, GRE, IND, ITA, JPN, KOR, NOR, POL, RUS, SPA, SRP, SRB, THA, TUR, UKR, VIE, WWW;
    }

    public static void initialize(boolean reloaded) {
        if (!reloaded) {
            initializeDisplay();
        }

        initializeSoundPref();
        initializeGamePref(reloaded);
    }

    private static void initializeDisplay() {
        logger.info("Initializing display settings...");
        DisplayConfig displayConf = DisplayConfig.readConfig();
        M_W = Gdx.graphics.getWidth();
        M_H = Gdx.graphics.getHeight();
        WIDTH = displayConf.getWidth();
        HEIGHT = displayConf.getHeight();
        MAX_FPS = displayConf.getMaxFPS();
        SAVED_WIDTH = WIDTH;
        SAVED_HEIGHT = HEIGHT;
        IS_FULLSCREEN = displayConf.getIsFullscreen();
        IS_W_FULLSCREEN = displayConf.getWFS();
        IS_V_SYNC = displayConf.getIsVsync();

        float aspectRatio = WIDTH / HEIGHT;
        boolean isUltrawide = false;
        isLetterbox = (aspectRatio > 2.34F || aspectRatio < 1.3332F);

        if (aspectRatio > 1.32F && aspectRatio < 1.34F) {
            isFourByThree = true;
        } else if (aspectRatio > 1.59F && aspectRatio < 1.61F) {
            isSixteenByTen = true;
        } else if (aspectRatio >= 1.78F) {

            if (aspectRatio > 1.78F) {
                isUltrawide = true;
            }
        }
        if (isLetterbox) {
            if (aspectRatio < 1.333F) {
                HEIGHT = MathUtils.round(WIDTH * 0.75F);
                HORIZ_LETTERBOX_AMT = (M_H - HEIGHT) / 2;
                HORIZ_LETTERBOX_AMT += 2;
                scale = WIDTH / 1920.0F;
                xScale = scale;
                renderScale = scale;
                yScale = HEIGHT / 1080.0F;
                isFourByThree = true;
            } else if (aspectRatio > 2.34F) {
                WIDTH = MathUtils.round(HEIGHT * 2.3333F);
                VERT_LETTERBOX_AMT = (M_W - WIDTH) / 2;
                VERT_LETTERBOX_AMT++;
                scale = (int) (HEIGHT * 1.77778F) / 1920.0F;
                xScale = WIDTH / 1920.0F;
                renderScale = xScale;
                yScale = scale;
                setXOffset();
            }
        } else if (isFourByThree) {

            scale = WIDTH / 1920.0F;
            xScale = scale;
            yScale = HEIGHT / 1080.0F;
            renderScale = yScale;
        } else if (isUltrawide) {

            scale = (int) (HEIGHT * 1.7777778F) / 1920.0F;
            xScale = WIDTH / 1920.0F;
            renderScale = xScale;
            yScale = scale;
            setXOffset();
            isLetterbox = true;
        } else {

            scale = WIDTH / 1920.0F;
            xScale = scale;
            yScale = scale;
            renderScale = scale;
        }

        SCROLL_SPEED = 75.0F * scale;
        MAP_SCROLL_SPEED = 75.0F * scale;
        DEFAULT_SCROLL_LIMIT = 50.0F * yScale;
        MAP_DST_Y = 150.0F * scale;
        CLICK_DIST_THRESHOLD = 30.0F * scale;
        CARD_DROP_END_Y = HEIGHT * 0.81F;
        POTION_W = isMobile ? (64.0F * scale) : (56.0F * scale);
        POTION_Y = isMobile ? (HEIGHT - 42.0F * scale) : (HEIGHT - 30.0F * scale);
        OPTION_Y = HEIGHT / 2.0F - 32.0F * yScale;
        EVENT_Y = HEIGHT / 2.0F - 128.0F * scale;
        CARD_VIEW_PAD_X = 40.0F * scale;
        CARD_VIEW_PAD_Y = 40.0F * scale;
        HOVER_BUTTON_RISE_AMOUNT = 8.0F * scale;
        CARD_SNAP_THRESHOLD = 1.0F * scale;
        UI_SNAP_THRESHOLD = 1.0F * scale;
        FOUR_BY_THREE_OFFSET_Y = 140.0F * yScale;
    }

    private static void setXOffset() {
        if (scale == 1.0F) {
            LETTERBOX_OFFSET_Y = 0.0F;

            return;
        }
        float offsetScale = xScale - 1.0F;
        if (offsetScale < 0.0F) {
            LETTERBOX_OFFSET_Y = 0.0F;

            return;
        }
        LETTERBOX_OFFSET_Y = (WIDTH - 1920) * offsetScale;
    }

    private static void initializeSoundPref() {
        logger.info("Initializing sound settings...");
        soundPref = SaveHelper.getPrefs("STSSound");

        try {
            soundPref.getBoolean("Ambience On");
            soundPref.getBoolean("Mute in Bg");
        } catch (Exception e) {

            soundPref.putBoolean("Ambience On", soundPref.getBoolean("Ambience On", true));
            soundPref.putBoolean("Mute in Bg", soundPref.getBoolean("Mute in Bg", true));
            soundPref.flush();
        }

        AMBIANCE_ON = soundPref.getBoolean("Ambience On", true);
        CardCrawlGame.MUTE_IF_BG = soundPref.getBoolean("Mute in Bg", true);
    }

    private static void initializeGamePref(boolean reloaded) {
        logger.info("Initializing game settings...");
        gamePref = SaveHelper.getPrefs("STSGameplaySettings");
        dailyPref = SaveHelper.getPrefs("STSDaily");

        try {
            gamePref.getBoolean("Summed Damage");
            gamePref.getBoolean("Blocked Damage");
            gamePref.getBoolean("Hand Confirmation");
            gamePref.getBoolean("Upload Data");
            gamePref.getBoolean("Particle Effects");
            gamePref.getBoolean("Fast Mode");
            gamePref.getBoolean("Show Card keys");
            gamePref.getBoolean("Bigger Text");
            gamePref.getBoolean("Long-press Enabled");
            gamePref.getBoolean("Screen Shake");
            gamePref.getBoolean("Playtester Art");
            gamePref.getBoolean("Controller Enabled");
            gamePref.getBoolean("Touchscreen Enabled");
        } catch (Exception e) {

            gamePref.putBoolean("Summed Damage", gamePref.getBoolean("Summed Damage", false));
            gamePref.putBoolean("Blocked Damage", gamePref.getBoolean("Blocked Damage", false));
            gamePref.putBoolean("Hand Confirmation", gamePref.getBoolean("Hand Confirmation", false));
            gamePref.putBoolean("Upload Data", gamePref.getBoolean("Upload Data", true));
            gamePref.putBoolean("Particle Effects", gamePref.getBoolean("Particle Effects", false));
            gamePref.putBoolean("Fast Mode", gamePref.getBoolean("Fast Mode", false));
            gamePref.putBoolean("Show Card keys", gamePref.getBoolean("Show Card keys", false));
            gamePref.putBoolean("Bigger Text", gamePref.getBoolean("Bigger Text", false));
            gamePref.putBoolean("Long-press Enabled", gamePref.getBoolean("Long-press Enabled", false));
            gamePref.putBoolean("Screen Shake", gamePref.getBoolean("Screen Shake", true));
            gamePref.putBoolean("Playtester Art", gamePref.getBoolean("Playtester Art", false));
            gamePref.putBoolean("Controller Enabled", gamePref.getBoolean("Controller Enabled", true));
            gamePref.putBoolean("Touchscreen Enabled", gamePref.getBoolean("Touchscreen Enabled", false));
            if (!reloaded) {
                setLanguage(gamePref.getString("LANGUAGE", GameLanguage.ENG.name()), true);
            }
            gamePref.flush();
        }

        SHOW_DMG_SUM = gamePref.getBoolean("Summed Damage", false);
        SHOW_DMG_BLOCK = gamePref.getBoolean("Blocked Damage", false);
        FAST_HAND_CONF = gamePref.getBoolean("Hand Confirmation", false);
        UPLOAD_DATA = gamePref.getBoolean("Upload Data", true);
        DISABLE_EFFECTS = gamePref.getBoolean("Particle Effects", false);
        FAST_MODE = gamePref.getBoolean("Fast Mode", false);
        SHOW_CARD_HOTKEYS = gamePref.getBoolean("Show Card keys", false);
        BIG_TEXT_MODE = gamePref.getBoolean("Bigger Text", false);
        USE_LONG_PRESS = gamePref.getBoolean("Long-press Enabled", false);
        SCREEN_SHAKE = gamePref.getBoolean("Screen Shake", true);
        PLAYTESTER_ART_MODE = gamePref.getBoolean("Playtester Art", false);
        CONTROLLER_ENABLED = gamePref.getBoolean("Controller Enabled", true);
        TOUCHSCREEN_ENABLED = gamePref.getBoolean("Touchscreen Enabled", false);

        if (TOUCHSCREEN_ENABLED || isConsoleBuild) {
            isTouchScreen = true;
        }

        if (!reloaded) {
            setLanguage(gamePref.getString("LANGUAGE", GameLanguage.ENG.name()), true);
        }
    }

    public static void setLanguage(GameLanguage key, boolean initial) {
        language = key;
        if (initial) {
            switch (language) {
                case ZHS:
                case ZHT:
                    manualAndAutoLineBreak = true;
                    lineBreakViaCharacter = true;
                    usesOrdinal = false;
                    removeAtoZSort = true;
                    break;
                case JPN:
                    lineBreakViaCharacter = true;
                    usesOrdinal = false;
                    if (isConsoleBuild) {
                        manualLineBreak = true;
                        leftAlignCards = true;
                    } else {
                        manualAndAutoLineBreak = true;
                        manualLineBreak = false;
                        leftAlignCards = false;
                    }
                    removeAtoZSort = true;
                    break;
                case ENG:
                    lineBreakViaCharacter = false;
                    usesOrdinal = true;
                    break;
                case DUT:
                case EPO:
                case PTB:
                case FIN:
                case FRA:
                case DEU:
                case GRE:
                case IND:
                case ITA:
                case KOR:
                case NOR:
                case POL:
                case RUS:
                case SPA:
                case SRP:
                case SRB:
                case THA:
                case UKR:
                case TUR:
                case VIE:
                    lineBreakViaCharacter = false;
                    usesOrdinal = false;
                    break;
                default:
                    logger.info("[ERROR] Unspecified language: " + key.toString());
                    lineBreakViaCharacter = false;
                    usesOrdinal = true;
                    break;
            }
        }
        gamePref.putString("LANGUAGE", key.name());
    }

    public static void setLanguage(String langStr, boolean initial) {
        try {
            GameLanguage langKey = GameLanguage.valueOf(langStr);
            setLanguage(langKey, initial);
        } catch (IllegalArgumentException ex) {
            setLanguageLegacy(langStr, initial);
        }
    }

    public static void setLanguageLegacy(String key, boolean initial) {
        switch (key) {
            case "English":
                language = GameLanguage.ENG;
                if (initial) {
                    lineBreakViaCharacter = false;
                    usesOrdinal = true;
                }
                break;
            case "Brazilian Portuguese":
                language = GameLanguage.PTB;
                if (initial) {
                    lineBreakViaCharacter = false;
                    usesOrdinal = false;
                }
                break;
            case "Chinese (Simplified)":
                language = GameLanguage.ZHS;
                if (initial) {
                    lineBreakViaCharacter = true;
                    usesOrdinal = false;
                }
                break;
            case "Chinese (Traditional)":
                language = GameLanguage.ZHT;
                if (initial) {
                    lineBreakViaCharacter = true;
                    usesOrdinal = false;
                }
                break;
            case "Finnish":
                language = GameLanguage.FIN;
                if (initial) {
                    lineBreakViaCharacter = false;
                    usesOrdinal = false;
                }
                break;
            case "French":
                language = GameLanguage.FRA;
                if (initial) {
                    lineBreakViaCharacter = false;
                    usesOrdinal = false;
                }
                break;
            case "German":
                language = GameLanguage.DEU;
                if (initial) {
                    lineBreakViaCharacter = false;
                    usesOrdinal = false;
                }
                break;
            case "Greek":
                language = GameLanguage.GRE;
                if (initial) {
                    lineBreakViaCharacter = false;
                    usesOrdinal = false;
                }
                break;
            case "Italian":
                language = GameLanguage.ITA;
                if (initial) {
                    lineBreakViaCharacter = false;
                    usesOrdinal = false;
                }
                break;
            case "Indonesian":
                language = GameLanguage.IND;
                if (initial) {
                    lineBreakViaCharacter = false;
                    usesOrdinal = false;
                }
                break;
            case "Japanese":
                language = GameLanguage.JPN;
                if (initial) {
                    lineBreakViaCharacter = true;
                    usesOrdinal = false;
                }
                break;
            case "Korean":
                language = GameLanguage.KOR;
                if (initial) {
                    lineBreakViaCharacter = false;
                    usesOrdinal = false;
                }
                break;
            case "Norwegian":
                language = GameLanguage.NOR;
                if (initial) {
                    lineBreakViaCharacter = false;
                    usesOrdinal = false;
                }
                break;
            case "Polish":
                language = GameLanguage.POL;
                if (initial) {
                    lineBreakViaCharacter = false;
                    usesOrdinal = false;
                }
                break;
            case "Russian":
                language = GameLanguage.RUS;
                if (initial) {
                    lineBreakViaCharacter = false;
                    usesOrdinal = false;
                }
                break;
            case "Spanish":
                language = GameLanguage.SPA;
                if (initial) {
                    lineBreakViaCharacter = false;
                    usesOrdinal = false;
                }
                break;
            case "Serbian-Cyrillic":
                language = GameLanguage.SRP;
                if (initial) {
                    lineBreakViaCharacter = false;
                    usesOrdinal = false;
                }
                break;
            case "Serbian-Latin":
                language = GameLanguage.SRB;
                if (initial) {
                    lineBreakViaCharacter = false;
                    usesOrdinal = false;
                }
                break;
            case "Thai":
                language = GameLanguage.THA;
                if (initial) {
                    lineBreakViaCharacter = false;
                    usesOrdinal = false;
                }
                break;
            case "Turkish":
                language = GameLanguage.TUR;
                if (initial) {
                    lineBreakViaCharacter = false;
                    usesOrdinal = false;
                }
                break;
            case "Ukrainian":
                language = GameLanguage.UKR;
                if (initial) {
                    lineBreakViaCharacter = false;
                    usesOrdinal = false;
                }
                break;
            case "Vietnamese":
                language = GameLanguage.VIE;
                if (initial) {
                    lineBreakViaCharacter = false;
                    usesOrdinal = false;
                }
            default:
                language = GameLanguage.ENG;
                if (initial) {
                    lineBreakViaCharacter = false;
                    usesOrdinal = true;
                }
                break;
        }

        gamePref.putString("LANGUAGE", key);
    }

    public static boolean isStandardRun() {
        return (!isDailyRun && !isTrial && !seedSet);
    }

    public static boolean treatEverythingAsUnlocked() {
        return (isDailyRun || isTrial);
    }

    public static void setFinalActAvailability() {
        isFinalActAvailable = ((CardCrawlGame.playerPref.getBoolean(AbstractPlayer.PlayerClass.IRONCLAD.name() + "_WIN",
                false)
                && CardCrawlGame.playerPref.getBoolean(AbstractPlayer.PlayerClass.THE_SILENT.name() + "_WIN", false)
                && CardCrawlGame.playerPref.getBoolean(AbstractPlayer.PlayerClass.DEFECT.name() + "_WIN", false)
                && !isDailyRun && !isTrial) || CustomModeScreen.finalActAvailable);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\core\Settings.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


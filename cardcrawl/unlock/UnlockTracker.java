package com.megacrit.cardcrawl.unlock;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.helpers.Prefs;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.helpers.SaveHelper;
import com.megacrit.cardcrawl.screens.stats.AchievementItem;
import com.megacrit.cardcrawl.screens.stats.StatsScreen;
import com.megacrit.cardcrawl.unlock.cards.defect.*;
import com.megacrit.cardcrawl.unlock.cards.ironclad.*;
import com.megacrit.cardcrawl.unlock.cards.silent.*;
import com.megacrit.cardcrawl.unlock.cards.watcher.*;
import com.megacrit.cardcrawl.unlock.relics.defect.*;
import com.megacrit.cardcrawl.unlock.relics.ironclad.*;
import com.megacrit.cardcrawl.unlock.relics.silent.*;
import com.megacrit.cardcrawl.unlock.relics.watcher.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class UnlockTracker {
    private static final Logger logger = LogManager.getLogger(UnlockTracker.class.getName());
    public static Prefs unlockPref;
    public static Prefs seenPref;
    public static Prefs betaCardPref;
    public static HashMap<String, String> unlockReqs = new HashMap<>();
    public static Prefs bossSeenPref;
    public static Prefs relicSeenPref;
    public static Prefs achievementPref;
    public static Prefs unlockProgress;
    public static ArrayList<String> lockedCards = new ArrayList<>();
    public static ArrayList<String> lockedCharacters = new ArrayList<>();
    public static ArrayList<String> lockedLoadouts = new ArrayList<>();
    public static ArrayList<String> lockedRelics = new ArrayList<>();
    public static int lockedRedCardCount;
    public static int unlockedRedCardCount;
    public static int lockedGreenCardCount;
    public static int unlockedGreenCardCount;
    public static int lockedBlueCardCount;
    public static int unlockedBlueCardCount;
    public static int lockedPurpleCardCount;
    public static int unlockedPurpleCardCount;
    public static int lockedRelicCount;
    public static int unlockedRelicCount;
    private static final int STARTING_UNLOCK_COST = 300;

    public static void initialize() {
        achievementPref = SaveHelper.getPrefs("STSAchievements");
        unlockPref = SaveHelper.getPrefs("STSUnlocks");
        unlockProgress = SaveHelper.getPrefs("STSUnlockProgress");
        seenPref = SaveHelper.getPrefs("STSSeenCards");
        betaCardPref = SaveHelper.getPrefs("STSBetaCardPreference");
        bossSeenPref = SaveHelper.getPrefs("STSSeenBosses");
        relicSeenPref = SaveHelper.getPrefs("STSSeenRelics");
        refresh();
    }

    public static void retroactiveUnlock() {
        ArrayList<String> cardKeys = new ArrayList<>();
        ArrayList<String> relicKeys = new ArrayList<>();
        ArrayList<AbstractUnlock> bundle = new ArrayList<>();

        appendRetroactiveUnlockList(AbstractPlayer.PlayerClass.IRONCLAD, unlockProgress

                .getInteger(AbstractPlayer.PlayerClass.IRONCLAD.toString() + "UnlockLevel", -1), bundle, cardKeys,
                relicKeys);

        appendRetroactiveUnlockList(AbstractPlayer.PlayerClass.THE_SILENT, unlockProgress

                .getInteger(AbstractPlayer.PlayerClass.THE_SILENT.toString() + "UnlockLevel", -1), bundle, cardKeys,
                relicKeys);

        appendRetroactiveUnlockList(AbstractPlayer.PlayerClass.DEFECT, unlockProgress

                .getInteger(AbstractPlayer.PlayerClass.DEFECT.toString() + "UnlockLevel", -1), bundle, cardKeys,
                relicKeys);

        appendRetroactiveUnlockList(AbstractPlayer.PlayerClass.WATCHER, unlockProgress

                .getInteger(AbstractPlayer.PlayerClass.WATCHER.toString() + "UnlockLevel", -1), bundle, cardKeys,
                relicKeys);

        boolean changed = false;

        for (String k : cardKeys) {
            if (unlockPref.getInteger(k) != 2) {
                unlockPref.putInteger(k, 2);
                changed = true;
                logger.info("RETROACTIVE CARD UNLOCK:  " + k);
            }
        }

        for (String k : relicKeys) {
            if (unlockPref.getInteger(k) != 2) {
                unlockPref.putInteger(k, 2);
                changed = true;
                logger.info("RETROACTIVE RELIC UNLOCK: " + k);
            }
        }

        if (isCharacterLocked("Watcher") && !isCharacterLocked("Defect") && (isAchievementUnlocked("RUBY") ||
                isAchievementUnlocked("EMERALD") || isAchievementUnlocked("SAPPHIRE"))) {

            unlockPref.putInteger("Watcher", 2);
            lockedCharacters.remove("Watcher");
            changed = true;
        }

        if (changed) {
            logger.info("RETRO UNLOCKED, SAVING");
            unlockPref.flush();
        }
    }

    private static void appendRetroactiveUnlockList(AbstractPlayer.PlayerClass c, int lvl,
            ArrayList<AbstractUnlock> bundle, ArrayList<String> cardKeys, ArrayList<String> relicKeys) {
        while (lvl > 0) {
            bundle = getUnlockBundle(c, lvl - 1);
            for (AbstractUnlock u : bundle) {
                if (u.type == AbstractUnlock.UnlockType.RELIC) {
                    logger.info(u.key + " should be unlocked.");
                    relicKeys.add(u.key);
                    continue;
                }
                if (u.type == AbstractUnlock.UnlockType.CARD) {
                    logger.info(u.key + " should be unlocked.");
                    cardKeys.add(u.key);
                }
            }
            lvl--;
        }
    }

    public static void refresh() {
        lockedCards.clear();
        lockedCharacters.clear();
        lockedLoadouts.clear();
        lockedRelics.clear();

        addCard("Havoc");
        addCard("Sentinel");
        addCard("Exhume");

        addCard("Wild Strike");
        addCard("Evolve");
        addCard("Immolate");

        addCard("Heavy Blade");
        addCard("Spot Weakness");
        addCard("Limit Break");

        addCard("Concentrate");
        addCard("Setup");
        addCard("Grand Finale");

        addCard("Cloak And Dagger");
        addCard("Accuracy");
        addCard("Storm of Steel");

        addCard("Bane");
        addCard("Catalyst");
        addCard("Corpse Explosion");

        addCard("Rebound");
        addCard("Undo");
        addCard("Echo Form");

        addCard("Turbo");
        addCard("Sunder");
        addCard("Meteor Strike");

        addCard("Hyperbeam");
        addCard("Recycle");
        addCard("Core Surge");

        addCard("Prostrate");
        addCard("Blasphemy");
        addCard("Devotion");

        addCard("ForeignInfluence");
        addCard("Alpha");
        addCard("MentalFortress");

        addCard("SpiritShield");
        addCard("Wish");
        addCard("Wireheading");

        addCharacter("The Silent");
        addCharacter("Defect");
        addCharacter("Watcher");

        addRelic("Omamori");
        addRelic("Prayer Wheel");
        addRelic("Shovel");

        addRelic("Art of War");
        addRelic("The Courier");
        addRelic("Pandora's Box");

        addRelic("Blue Candle");
        addRelic("Dead Branch");
        addRelic("Singing Bowl");

        addRelic("Du-Vu Doll");
        addRelic("Smiling Mask");
        addRelic("Tiny Chest");

        addRelic("Cables");
        addRelic("DataDisk");
        addRelic("Emotion Chip");

        addRelic("Runic Capacitor");
        addRelic("Turnip");
        addRelic("Symbiotic Virus");

        addRelic("Akabeko");
        addRelic("Yang");
        addRelic("CeramicFish");

        addRelic("StrikeDummy");
        addRelic("TeardropLocket");
        addRelic("CloakClasp");

        countUnlockedCards();
    }

    public static int incrementUnlockRamp(int currentCost) {
        switch (currentCost) {
            case 300:
                return 750;

            case 500:
                return 1000;
            case 750:
                return 1000;
            case 1000:
                return 1500;
            case 1500:
                return 2000;
            case 2000:
                return 2500;
            case 2500:
                return 3000;
            case 3000:
                return 3000;
            case 4000:
                return 4000;
        }
        return currentCost + 250;
    }

    public static void resetUnlockProgress(AbstractPlayer.PlayerClass c) {
        unlockProgress.putInteger(c.toString() + "UnlockLevel", 0);
        unlockProgress.putInteger(c.toString() + "Progress", 0);
        unlockProgress.putInteger(c.toString() + "CurrentCost", 300);
        unlockProgress.putInteger(c.toString() + "TotalScore", 0);
        unlockProgress.putInteger(c.toString() + "HighScore", 0);
    }

    public static int getUnlockLevel(AbstractPlayer.PlayerClass c) {
        return unlockProgress.getInteger(c.toString() + "UnlockLevel", 0);
    }

    public static int getCurrentProgress(AbstractPlayer.PlayerClass c) {
        return unlockProgress.getInteger(c.toString() + "Progress", 0);
    }

    public static int getCurrentScoreCost(AbstractPlayer.PlayerClass c) {
        return unlockProgress.getInteger(c.toString() + "CurrentCost", 300);
    }

    public static void addScore(AbstractPlayer.PlayerClass c, int scoreGained) {
        String key_unlock_level = c.toString() + "UnlockLevel";
        String key_progress = c.toString() + "Progress";
        String key_current_cost = c.toString() + "CurrentCost";
        String key_total_score = c.toString() + "TotalScore";
        String key_high_score = c.toString() + "HighScore";
        logger.info("Keys");
        logger.info(key_unlock_level);
        logger.info(key_progress);
        logger.info(key_current_cost);
        logger.info(key_total_score);
        logger.info(key_high_score);

        int p = unlockProgress.getInteger(key_progress, 0);
        p += scoreGained;

        if (p >= unlockProgress.getInteger(key_current_cost, 300)) {
            logger.info("[DEBUG] Level up!");

            int lvl = unlockProgress.getInteger(key_unlock_level, 0);
            lvl++;
            unlockProgress.putInteger(key_unlock_level, lvl);

            p -= unlockProgress.getInteger(key_current_cost, 300);
            unlockProgress.putInteger(key_progress, p);
            logger.info("[DEBUG] Score Progress: " + key_progress);

            int current_cost = unlockProgress.getInteger(key_current_cost, 300);
            unlockProgress.putInteger(key_current_cost, incrementUnlockRamp(current_cost));

            if (p > unlockProgress.getInteger(key_current_cost, 300)) {
                unlockProgress.putInteger(key_progress, unlockProgress

                        .getInteger(key_current_cost, 300) - 1);
                logger.info("Overfloat maxes out next level");
            }
        } else {

            unlockProgress.putInteger(key_progress, p);
        }

        int total = unlockProgress.getInteger(key_total_score, 0);
        total += scoreGained;
        unlockProgress.putInteger(key_total_score, total);
        logger.info("[DEBUG] Total score: " + total);

        int highscore = unlockProgress.getInteger(key_high_score, 0);
        if (scoreGained > highscore) {
            unlockProgress.putInteger(key_high_score, scoreGained);
            logger.info("[DEBUG] New high score: " + scoreGained);
        }

        unlockProgress.flush();
    }

    public static void countUnlockedCards() {
        ArrayList<String> tmp = new ArrayList<>();
        int count = 0;

        tmp.add("Havoc");
        tmp.add("Sentinel");
        tmp.add("Exhume");

        tmp.add("Wild Strike");
        tmp.add("Evolve");
        tmp.add("Immolate");

        tmp.add("Heavy Blade");
        tmp.add("Spot Weakness");
        tmp.add("Limit Break");

        for (String s : tmp) {
            if (!isCardLocked(s)) {
                count++;
            }
        }

        lockedRedCardCount = tmp.size();
        unlockedRedCardCount = count;
        tmp.clear();
        count = 0;

        tmp.add("Concentrate");
        tmp.add("Setup");
        tmp.add("Grand Finale");

        tmp.add("Cloak And Dagger");
        tmp.add("Accuracy");
        tmp.add("Storm of Steel");

        tmp.add("Bane");
        tmp.add("Catalyst");
        tmp.add("Corpse Explosion");

        for (String s : tmp) {
            if (!isCardLocked(s)) {
                count++;
            }
        }

        lockedGreenCardCount = tmp.size();
        unlockedGreenCardCount = count;
        tmp.clear();
        count = 0;

        tmp.add("Rebound");
        tmp.add("Undo");
        tmp.add("Echo Form");

        tmp.add("Turbo");
        tmp.add("Sunder");
        tmp.add("Meteor Strike");

        tmp.add("Hyperbeam");
        tmp.add("Recycle");
        tmp.add("Core Surge");

        for (String s : tmp) {
            if (!isCardLocked(s)) {
                count++;
            }
        }

        lockedBlueCardCount = tmp.size();
        unlockedBlueCardCount = count;
        tmp.clear();
        count = 0;

        tmp.add("Prostrate");
        tmp.add("Blasphemy");
        tmp.add("Devotion");

        tmp.add("ForeignInfluence");
        tmp.add("Alpha");
        tmp.add("MentalFortress");

        tmp.add("SpiritShield");
        tmp.add("Wish");
        tmp.add("Wireheading");

        for (String s : tmp) {
            if (!isCardLocked(s)) {
                count++;
            }
        }

        lockedPurpleCardCount = tmp.size();
        unlockedPurpleCardCount = count;
        tmp.clear();
        count = 0;

        tmp.add("Omamori");
        tmp.add("Prayer Wheel");
        tmp.add("Shovel");

        tmp.add("Art of War");
        tmp.add("The Courier");
        tmp.add("Pandora's Box");

        tmp.add("Blue Candle");
        tmp.add("Dead Branch");
        tmp.add("Singing Bowl");

        tmp.add("Du-Vu Doll");
        tmp.add("Smiling Mask");
        tmp.add("Tiny Chest");

        tmp.add("Cables");
        tmp.add("DataDisk");
        tmp.add("Emotion Chip");
        tmp.add("Runic Capacitor");
        tmp.add("Turnip");
        tmp.add("Symbiotic Virus");

        tmp.add("Akabeko");
        tmp.add("Yang");
        tmp.add("CeramicFish");

        tmp.add("StrikeDummy");
        tmp.add("TeardropLocket");
        tmp.add("CloakClasp");

        for (String s : tmp) {
            if (!isRelicLocked(s)) {
                count++;
            }
        }

        lockedRelicCount = tmp.size();
        unlockedRelicCount = count;

        logger.info("RED UNLOCKS:   " + unlockedRedCardCount + "/" + lockedRedCardCount);
        logger.info("GREEN UNLOCKS: " + unlockedGreenCardCount + "/" + lockedGreenCardCount);
        logger.info("BLUE UNLOCKS: " + unlockedBlueCardCount + "/" + lockedBlueCardCount);
        logger.info("PURPLE UNLOCKS: " + unlockedPurpleCardCount + "/" + lockedPurpleCardCount);
        logger.info("RELIC UNLOCKS: " + unlockedRelicCount + "/" + lockedRelicCount);
        logger.info("CARDS SEEN:    " + seenPref.get().keySet().size() + "/" + CardLibrary.totalCardCount);
        logger.info("RELICS SEEN:   " + relicSeenPref.get().keySet().size() + "/" + RelicLibrary.totalRelicCount);
    }

    public static String getCardsSeenString() {
        return (CardLibrary.seenRedCards + CardLibrary.seenGreenCards + CardLibrary.seenBlueCards
                + CardLibrary.seenPurpleCards + CardLibrary.seenColorlessCards + CardLibrary.seenCurseCards) + "/"
                + CardLibrary.totalCardCount;
    }

    public static String getRelicsSeenString() {
        return RelicLibrary.seenRelics + "/" + RelicLibrary.totalRelicCount;
    }

    public static void addCard(String key) {
        if (unlockPref.getString(key).equals("true")) {
            unlockPref.putInteger(key, 2);
            logger.info("Converting " + key + " from bool to int");
            unlockPref.flush();
        } else if (unlockPref.getString(key).equals("false")) {
            unlockPref.putInteger(key, 0);
            logger.info("Converting " + key + " from bool to int");
            unlockPref.flush();
        }

        if (unlockPref.getInteger(key, 0) != 2) {
            lockedCards.add(key);
        }
    }

    public static void addCharacter(String key) {
        if (unlockPref.getString(key).equals("true")) {
            unlockPref.putInteger(key, 2);
            logger.info("Converting " + key + " from bool to int");
            unlockPref.flush();
        } else if (unlockPref.getString(key).equals("false")) {
            unlockPref.putInteger(key, 0);
            logger.info("Converting " + key + " from bool to int");
            unlockPref.flush();
        }

        if (unlockPref.getInteger(key, 0) != 2) {
            lockedCharacters.add(key);
        }
    }

    public static void addRelic(String key) {
        if (unlockPref.getInteger(key, 0) != 2) {
            lockedRelics.add(key);
        }
    }

    public static void unlockAchievement(String key) {
        if (Settings.isModded || Settings.isShowBuild || !Settings.isStandardRun()) {
            return;
        }

        CardCrawlGame.publisherIntegration.unlockAchievement(key);

        if (!achievementPref.getBoolean(key, false)) {
            achievementPref.putBoolean(key, true);
            logger.info("Achievement Unlocked: " + key);
        }

        if (allAchievementsExceptPlatinumUnlocked() && !isAchievementUnlocked("ETERNAL_ONE")) {

            CardCrawlGame.publisherIntegration.unlockAchievement("ETERNAL_ONE");
            achievementPref.putBoolean("ETERNAL_ONE", true);
            logger.info("Achievement Unlocked: ETERNAL_ONE");
        }

        achievementPref.flush();
    }

    public static boolean allAchievementsExceptPlatinumUnlocked() {
        return (achievementPref.data.entrySet().size() >= 45);
    }

    public static boolean isAchievementUnlocked(String key) {
        return achievementPref.getBoolean(key, false);
    }

    public static void unlockLuckyDay() {
        if (Settings.isModded) {
            return;
        }
        String key = "LUCKY_DAY";
        CardCrawlGame.publisherIntegration.unlockAchievement(key);

        if (!achievementPref.getBoolean(key, false)) {
            achievementPref.putBoolean(key, true);
            achievementPref.flush();
            logger.info("Achievement Unlocked: " + key);
        }
    }

    public static void hardUnlock(String key) {
        if (Settings.isShowBuild) {
            return;
        }

        if (unlockPref.getInteger(key, 0) == 1) {
            unlockPref.putInteger(key, 2);
            unlockPref.flush();
            logger.info("Hard Unlock: " + key);
        }
    }

    public static void hardUnlockOverride(String key) {
        if (Settings.isShowBuild) {
            return;
        }

        unlockPref.putInteger(key, 2);
        unlockPref.flush();
        logger.info("Hard Unlock: " + key);
    }

    public static boolean isCardLocked(String key) {
        return lockedCards.contains(key);
    }

    public static void unlockCard(String key) {
        seenPref.putInteger(key, 1);
        seenPref.flush();
        unlockPref.putInteger(key, 2);
        unlockPref.flush();
        lockedCards.remove(key);

        if (CardLibrary.getCard(key) != null) {
            (CardLibrary.getCard(key)).isSeen = true;
            CardLibrary.getCard(key).unlock();
        }
    }

    public static boolean isCharacterLocked(String key) {
        if (key.equals("The Silent") && Settings.isDemo)
            return false;
        if (Settings.isAlpha) {
            return false;
        }
        return lockedCharacters.contains(key);
    }

    public static boolean isAscensionUnlocked(AbstractPlayer p) {
        int victories = StatsScreen.getVictory(p.getCharStat());

        if (victories > 0) {

            if (!achievementPref.getBoolean("ASCEND_0", false)) {
                unlockAchievement("ASCEND_0");
            }

            if (!achievementPref.getBoolean("ASCEND_10", false)) {
                StatsScreen.retroactiveAscend10Unlock(p.getPrefs());
            }

            if (!achievementPref.getBoolean("ASCEND_20", false)) {
                StatsScreen.retroactiveAscend20Unlock(p.getPrefs());
            }
            return true;
        }

        return false;
    }

    public static boolean isRelicLocked(String key) {
        return lockedRelics.contains(key);
    }

    public static void markCardAsSeen(String key) {
        if (CardLibrary.getCard(key) != null && !(CardLibrary.getCard(key)).isSeen) {
            (CardLibrary.getCard(key)).isSeen = true;
            seenPref.putInteger(key, 1);
            seenPref.flush();
        } else {
            logger.info("Already seen: " + key);
        }
    }

    public static boolean isCardSeen(String key) {
        return (seenPref.getInteger(key, 0) != 0);
    }

    public static void markRelicAsSeen(String key) {
        if (RelicLibrary.getRelic(key) != null && !(RelicLibrary.getRelic(key)).isSeen) {
            (RelicLibrary.getRelic(key)).isSeen = true;
            relicSeenPref.putInteger(key, 1);
            relicSeenPref.flush();
        } else if (Settings.isDebug) {
            logger.info("Already seen: " + key);
        }
    }

    public static boolean isRelicSeen(String key) {
        return (relicSeenPref.getInteger(key, 0) == 1);
    }

    public static void markBossAsSeen(String originalName) {
        if (bossSeenPref.getInteger(originalName) != 1) {
            bossSeenPref.putInteger(originalName, 1);
            bossSeenPref.flush();
        }
    }

    public static boolean isBossSeen(String key) {
        if (bossSeenPref.getInteger(key, 0) == 1) {
            return true;
        }
        return false;
    }

    public static ArrayList<AbstractUnlock> getUnlockBundle(AbstractPlayer.PlayerClass c, int unlockLevel) {
        ArrayList<AbstractUnlock> tmpBundle = new ArrayList<>();
        switch (c) {
            case IRONCLAD:
                switch (unlockLevel) {
                    case 0:
                        tmpBundle.add(new HeavyBladeUnlock());
                        tmpBundle.add(new SpotWeaknessUnlock());
                        tmpBundle.add(new LimitBreakUnlock());
                        break;
                    case 1:
                        tmpBundle.add(new OmamoriUnlock());
                        tmpBundle.add(new PrayerWheelUnlock());
                        tmpBundle.add(new ShovelUnlock());
                        break;
                    case 2:
                        tmpBundle.add(new WildStrikeUnlock());
                        tmpBundle.add(new EvolveUnlock());
                        tmpBundle.add(new ImmolateUnlock());
                        break;
                    case 3:
                        tmpBundle.add(new HavocUnlock());
                        tmpBundle.add(new SentinelUnlock());
                        tmpBundle.add(new ExhumeUnlock());
                        break;
                    case 4:
                        tmpBundle.add(new BlueCandleUnlock());
                        tmpBundle.add(new DeadBranchUnlock());
                        tmpBundle.add(new SingingBowlUnlock());
                        break;
                }

                break;

            case THE_SILENT:
                switch (unlockLevel) {
                    case 0:
                        tmpBundle.add(new BaneUnlock());
                        tmpBundle.add(new CatalystUnlock());
                        tmpBundle.add(new CorpseExplosionUnlock());
                        break;
                    case 1:
                        tmpBundle.add(new DuvuDollUnlock());
                        tmpBundle.add(new SmilingMaskUnlock());
                        tmpBundle.add(new TinyChestUnlock());
                        break;
                    case 2:
                        tmpBundle.add(new CloakAndDaggerUnlock());
                        tmpBundle.add(new AccuracyUnlock());
                        tmpBundle.add(new StormOfSteelUnlock());
                        break;
                    case 3:
                        tmpBundle.add(new ArtOfWarUnlock());
                        tmpBundle.add(new CourierUnlock());
                        tmpBundle.add(new PandorasBoxUnlock());
                        break;
                    case 4:
                        tmpBundle.add(new ConcentrateUnlock());
                        tmpBundle.add(new SetupUnlock());
                        tmpBundle.add(new GrandFinaleUnlock());
                        break;
                }

                break;

            case DEFECT:
                switch (unlockLevel) {
                    case 0:
                        tmpBundle.add(new ReboundUnlock());
                        tmpBundle.add(new UndoUnlock());
                        tmpBundle.add(new EchoFormUnlock());
                        break;
                    case 1:
                        tmpBundle.add(new TurboUnlock());
                        tmpBundle.add(new SunderUnlock());
                        tmpBundle.add(new MeteorStrikeUnlock());
                        break;
                    case 2:
                        tmpBundle.add(new HyperbeamUnlock());
                        tmpBundle.add(new RecycleUnlock());
                        tmpBundle.add(new NovaUnlock());
                        break;
                    case 3:
                        tmpBundle.add(new CablesUnlock());
                        tmpBundle.add(new TurnipUnlock());
                        tmpBundle.add(new RunicCapacitorUnlock());
                        break;
                    case 4:
                        tmpBundle.add(new EmotionChipUnlock());
                        tmpBundle.add(new VirusUnlock());
                        tmpBundle.add(new DataDiskUnlock());
                        break;
                }

                break;

            case WATCHER:
                switch (unlockLevel) {
                    case 0:
                        tmpBundle.add(new ProstrateUnlock());
                        tmpBundle.add(new BlasphemyUnlock());
                        tmpBundle.add(new DevotionUnlock());
                        break;
                    case 1:
                        tmpBundle.add(new ForeignInfluenceUnlock());
                        tmpBundle.add(new AlphaUnlock());
                        tmpBundle.add(new MentalFortressUnlock());
                        break;
                    case 2:
                        tmpBundle.add(new ClarityUnlock());
                        tmpBundle.add(new WishUnlock());
                        tmpBundle.add(new ForesightUnlock());
                        break;
                    case 3:
                        tmpBundle.add(new AkabekoUnlock());
                        tmpBundle.add(new YangUnlock());
                        tmpBundle.add(new CeramicFishUnlock());
                        break;
                    case 4:
                        tmpBundle.add(new StrikeDummyUnlock());
                        tmpBundle.add(new TeardropUnlock());
                        tmpBundle.add(new CloakClaspUnlock());
                        break;
                }

                break;
        }

        return tmpBundle;
    }

    public static void addCardUnlockToList(HashMap<String, AbstractUnlock> map, String key, AbstractUnlock unlock) {
        if (isCardLocked(key)) {
            map.put(key, unlock);
        }
    }

    public static void addRelicUnlockToList(HashMap<String, AbstractUnlock> map, String key, AbstractUnlock unlock) {
        if (isRelicLocked(key)) {
            map.put(key, unlock);
        }
    }

    public static float getCompletionPercentage() {
        float totalPercent = 0.0F;
        totalPercent += getAscensionProgress() * 0.3F;
        totalPercent += getUnlockProgress() * 0.25F;
        totalPercent += getAchievementProgress() * 0.35F;
        totalPercent += getSeenCardsProgress() * 0.05F;
        totalPercent += getSeenRelicsProgress() * 0.05F;
        return totalPercent * 100.0F;
    }

    private static float getAscensionProgress() {
        ArrayList<Prefs> allCharacterPrefs = CardCrawlGame.characterManager.getAllPrefs();
        int sum = 0;
        for (Prefs p : allCharacterPrefs) {
            sum += p.getInteger("ASCENSION_LEVEL", 0);
        }

        float retVal = sum / 60.0F;
        logger.info("Ascension Progress: " + retVal);
        if (retVal > 1.0F) {
            retVal = 1.0F;
        }
        return retVal;
    }

    private static float getUnlockProgress() {
        int sum = Math.min(getUnlockLevel(AbstractPlayer.PlayerClass.IRONCLAD), 5);
        sum += Math.min(getUnlockLevel(AbstractPlayer.PlayerClass.THE_SILENT), 5);
        sum += Math.min(getUnlockLevel(AbstractPlayer.PlayerClass.DEFECT), 5);
        sum += Math.min(getUnlockLevel(AbstractPlayer.PlayerClass.WATCHER), 5);

        float retVal = sum / 15.0F;
        logger.info("Unlock IC: " + getUnlockLevel(AbstractPlayer.PlayerClass.IRONCLAD));
        logger.info("Unlock Silent: " + getUnlockLevel(AbstractPlayer.PlayerClass.THE_SILENT));
        logger.info("Unlock Defect: " + getUnlockLevel(AbstractPlayer.PlayerClass.DEFECT));
        logger.info("Unlock Watcher: " + getUnlockLevel(AbstractPlayer.PlayerClass.WATCHER));
        logger.info("Unlock Progress: " + retVal);
        if (retVal > 1.0F) {
            retVal = 1.0F;
        }
        return retVal;
    }

    private static float getAchievementProgress() {
        int sum = 0;
        for (AchievementItem item : StatsScreen.achievements.items) {
            if (item.isUnlocked) {
                sum++;
            }
        }

        float retVal = sum / StatsScreen.achievements.items.size();
        logger.info("Achievement Progress: " + retVal);
        if (retVal > 1.0F) {
            retVal = 1.0F;
        }
        return retVal;
    }

    private static float getSeenCardsProgress() {
        int sum = 0;
        for (Map.Entry<String, AbstractCard> c : (Iterable<Map.Entry<String, AbstractCard>>) CardLibrary.cards
                .entrySet()) {
            if (((AbstractCard) c.getValue()).isSeen) {
                sum++;
            }
        }

        float retVal = sum / CardLibrary.cards.size();
        logger.info("Seen Cards Progress: " + retVal);
        if (retVal > 1.0F) {
            retVal = 1.0F;
        }
        return retVal;
    }

    private static float getSeenRelicsProgress() {
        float retVal = RelicLibrary.seenRelics / RelicLibrary.totalRelicCount;
        logger.info("Seen Relics Progress: " + retVal);
        if (retVal > 1.0F) {
            retVal = 1.0F;
        }
        return retVal;
    }

    public static long getTotalPlaytime() {
        return Settings.totalPlayTime;
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcraw\\unlock\
 * UnlockTracker.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

package com.megacrit.cardcrawl.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.localization.ScoreBonusStrings;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.screens.stats.StatsScreen;
import com.megacrit.cardcrawl.ui.buttons.ReturnToMenuButton;
import com.megacrit.cardcrawl.unlock.AbstractUnlock;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.UnlockTextEffect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public abstract class GameOverScreen {
    private static final Logger logger = LogManager.getLogger(GameOverScreen.class.getName());
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("DeathScreen");
    private static final String[] TEXT = uiStrings.TEXT;

    protected ReturnToMenuButton returnButton;
    public static boolean isVictory;
    protected ArrayList<AbstractUnlock> unlockBundle = null;

    protected ArrayList<GameOverStat> stats = new ArrayList<>();
    protected Color fadeBgColor = new Color(0.0F, 0.0F, 0.0F, 0.0F);
    protected Color whiteUiColor = new Color(1.0F, 1.0F, 1.0F, 0.0F);
    protected Color creamUiColor = Settings.CREAM_COLOR.cpy();
    private float progressBarX = 576.0F * Settings.xScale;
    private float progressBarWidth = 768.0F * Settings.xScale;
    protected boolean showingStats;
    protected float statsTimer = 0.0F;
    protected float statAnimateTimer = 0.0F;
    protected float progressBarTimer = 2.0F;
    protected float progressBarAlpha = 0.0F;
    protected static final float STAT_OFFSET_Y = 36.0F * Settings.scale;
    protected boolean maxLevel = false;
    protected float progressPercent;
    protected float unlockTargetProgress;
    protected static final float STAT_START_Y = Settings.HEIGHT / 2.0F - 20.0F * Settings.scale;
    protected float unlockTargetStart;
    protected float unlockProgress;
    protected long playtime;
    protected int score = 0;
    protected int unlockCost;
    protected int nextUnlockCost;
    protected int unlockLevel = 0;

    protected static final float STATS_TRANSITION_TIME = 0.5F;

    protected static final float STAT_ANIM_INTERVAL = 0.1F;
    protected static final float PROGRESS_BAR_ANIM_TIME = 2.0F;
    protected static final ScoreBonusStrings EXORDIUM_ELITE = CardCrawlGame.languagePack
            .getScoreString("Exordium Elites Killed");

    protected static final ScoreBonusStrings CITY_ELITE = CardCrawlGame.languagePack
            .getScoreString("City Elites Killed");

    protected static final ScoreBonusStrings BEYOND_ELITE = CardCrawlGame.languagePack
            .getScoreString("Beyond Elites Killed");

    protected static final ScoreBonusStrings BOSSES_SLAIN = CardCrawlGame.languagePack.getScoreString("Bosses Slain");
    protected static final ScoreBonusStrings ASCENSION = CardCrawlGame.languagePack.getScoreString("Ascension");
    protected static final ScoreBonusStrings CHAMPION = CardCrawlGame.languagePack.getScoreString("Champion");
    protected static final ScoreBonusStrings PERFECT = CardCrawlGame.languagePack.getScoreString("Perfect");
    protected static final ScoreBonusStrings BEYOND_PERFECT = CardCrawlGame.languagePack
            .getScoreString("Beyond Perfect");

    protected static final ScoreBonusStrings OVERKILL = CardCrawlGame.languagePack.getScoreString("Overkill");
    protected static final ScoreBonusStrings COMBO = CardCrawlGame.languagePack.getScoreString("Combo");
    protected static final ScoreBonusStrings POOPY = CardCrawlGame.languagePack.getScoreString("Poopy");
    protected static final ScoreBonusStrings SPEEDSTER = CardCrawlGame.languagePack.getScoreString("Speedster");
    protected static final ScoreBonusStrings LIGHT_SPEED = CardCrawlGame.languagePack.getScoreString("Light Speed");
    protected static final ScoreBonusStrings MONEY_MONEY = CardCrawlGame.languagePack.getScoreString("Money Money");
    protected static final ScoreBonusStrings RAINING_MONEY = CardCrawlGame.languagePack.getScoreString("Raining Money");
    protected static final ScoreBonusStrings I_LIKE_GOLD = CardCrawlGame.languagePack.getScoreString("I Like Gold");
    protected static final ScoreBonusStrings HIGHLANDER = CardCrawlGame.languagePack.getScoreString("Highlander");
    protected static final ScoreBonusStrings SHINY = CardCrawlGame.languagePack.getScoreString("Shiny");
    protected static final ScoreBonusStrings COLLECTOR = CardCrawlGame.languagePack.getScoreString("Collector");
    protected static final ScoreBonusStrings PAUPER = CardCrawlGame.languagePack.getScoreString("Pauper");
    protected static final ScoreBonusStrings LIBRARIAN = CardCrawlGame.languagePack.getScoreString("Librarian");
    protected static final ScoreBonusStrings ENCYCLOPEDIAN = CardCrawlGame.languagePack.getScoreString("Encyclopedian");
    protected static final ScoreBonusStrings WELL_FED = CardCrawlGame.languagePack.getScoreString("Well Fed");
    protected static final ScoreBonusStrings STUFFED = CardCrawlGame.languagePack.getScoreString("Stuffed");
    protected static final ScoreBonusStrings CURSES = CardCrawlGame.languagePack.getScoreString("Curses");
    protected static final ScoreBonusStrings MYSTERY_MACHINE = CardCrawlGame.languagePack
            .getScoreString("Mystery Machine");

    protected static final ScoreBonusStrings ON_MY_OWN_TERMS = CardCrawlGame.languagePack
            .getScoreString("On My Own Terms");

    protected static final ScoreBonusStrings HEARTBREAKER = CardCrawlGame.languagePack.getScoreString("Heartbreaker");

    protected static boolean IS_POOPY = false;

    protected static boolean IS_SPEEDSTER = false;
    protected static boolean IS_LIGHT_SPEED = false;
    protected static boolean IS_HIGHLANDER = false;
    protected static int IS_FULL_SET = 0;

    protected static boolean IS_SHINY = false;

    protected static boolean IS_PAUPER = false;
    protected static boolean IS_LIBRARY = false;
    protected static boolean IS_ENCYCLOPEDIA = false;
    protected static boolean IS_WELL_FED = false;
    protected static boolean IS_STUFFED = false;
    protected static boolean IS_CURSES = false;
    protected static boolean IS_ON_MY_OWN = false;
    protected static boolean IS_MONEY_MONEY = false;
    protected static boolean IS_RAINING_MONEY = false;
    protected static boolean IS_I_LIKE_GOLD = false;
    protected static boolean IS_MYSTERY_MACHINE = false;
    protected static final int POOPY_SCORE = -1;
    protected static final int SPEEDER_SCORE = 25;
    protected static final int LIGHT_SPEED_SCORE = 50;
    protected static final int HIGHLANDER_SCORE = 100;
    protected static final int FULL_SET_SCORE = 25;
    protected static final int SHINY_SCORE = 50;
    protected static final int PAUPER_SCORE = 50;
    protected static final int LIBRARY_SCORE = 25;
    protected static final int ENCYCLOPEDIA_SCORE = 50;
    protected static final int WELL_FED_SCORE = 25;
    protected static final int STUFFED_SCORE = 50;
    protected static final int CURSES_SCORE = 100;
    protected static final int ON_MY_OWN_SCORE = 50;
    protected static final int MONEY_MONEY_SCORE = 25;
    protected static final int RAINING_MONEY_SCORE = 50;
    protected static final int I_LIKE_GOLD_SCORE = 75;
    protected static final int CHAMPION_SCORE = 25;
    protected static final int PERFECT_SCORE = 50;
    protected static final int BEYOND_PERFECT_SCORE = 200;
    protected static final int OVERKILL_SCORE = 25;
    protected static final int COMBO_SCORE = 25;
    protected static final int MYSTERY_MACHINE_SCORE = 25;
    protected static final int HEARTBREAKER_SCORE = 250;
    protected static int floorPoints;
    protected static int monsterPoints;
    protected static int elite1Points;
    protected static int elite2Points;
    protected static int elite3Points;
    protected static int bossPoints;
    protected static int ascensionPoints;
    protected static final int FLOOR_MULTIPLIER = 5;
    protected static final int ENEMY_MULTIPLIER = 2;
    protected static final int ELITE_MULTIPLIER_1 = 10;
    protected static final int ELITE_MULTIPLIER_2 = 20;
    protected static final int ELITE_MULTIPLIER_3 = 30;
    protected static final int BOSS_MULTIPLIER = 50;
    protected static final float ASCENSION_MULTIPLIER = 0.05F;
    protected boolean playedWhir = false;
    protected long whirId;

    public static void resetScoreChecks() {
        IS_POOPY = false;
        IS_SPEEDSTER = false;
        IS_LIGHT_SPEED = false;
        IS_HIGHLANDER = false;
        IS_FULL_SET = 0;
        IS_SHINY = false;
        IS_PAUPER = false;
        IS_LIBRARY = false;
        IS_ENCYCLOPEDIA = false;
        IS_WELL_FED = false;
        IS_STUFFED = false;
        IS_CURSES = false;
        IS_ON_MY_OWN = false;
        IS_MONEY_MONEY = false;
        IS_RAINING_MONEY = false;
        IS_I_LIKE_GOLD = false;
        IS_MYSTERY_MACHINE = false;
    }

    protected void animateProgressBar() {
        if (this.maxLevel) {
            return;
        }

        this.progressBarTimer -= Gdx.graphics.getDeltaTime();
        if (this.progressBarTimer < 0.0F) {
            this.progressBarTimer = 0.0F;
        }

        if (this.progressBarTimer > 2.0F) {
            return;
        }

        if (!this.playedWhir) {
            this.playedWhir = true;
            this.whirId = CardCrawlGame.sound.play("UNLOCK_WHIR");
        }

        this.unlockProgress = Interpolation.pow2In.apply(this.unlockTargetProgress, this.unlockTargetStart,
                this.progressBarTimer / 2.0F);

        if (this.unlockProgress >= this.unlockCost && this.unlockLevel != 5) {
            if (this.unlockLevel == 4) {
                this.unlockProgress = this.unlockCost;
                this.unlockLevel++;
                AbstractDungeon.topLevelEffects.add(new UnlockTextEffect());
            } else {
                this.unlockTargetProgress = this.score - this.unlockCost - this.unlockTargetStart;
                this.progressBarTimer = 3.0F;
                AbstractDungeon.topLevelEffects.add(new UnlockTextEffect());
                CardCrawlGame.sound.stop("UNLOCK_WHIR", this.whirId);
                this.playedWhir = false;
                this.unlockProgress = 0.0F;
                this.unlockTargetStart = 0.0F;

                if (this.unlockTargetProgress > (this.nextUnlockCost - 1)) {
                    this.unlockTargetProgress = (this.nextUnlockCost - 1);
                }

                this.unlockCost = this.nextUnlockCost;
                this.unlockLevel++;
            }
        }

        this.progressPercent = this.unlockProgress / this.unlockCost;
    }

    protected void calculateUnlockProgress() {
        this.score = calcScore(isVictory);

        this.unlockLevel = UnlockTracker.getUnlockLevel(AbstractDungeon.player.chosenClass);
        if (this.unlockLevel >= 5) {
            this.maxLevel = true;

            return;
        }
        if (this.score == 0) {
            this.playedWhir = true;
        }

        this.unlockProgress = UnlockTracker.getCurrentProgress(AbstractDungeon.player.chosenClass);
        this.unlockTargetStart = this.unlockProgress;
        this.unlockCost = UnlockTracker.getCurrentScoreCost(AbstractDungeon.player.chosenClass);
        this.unlockTargetProgress = this.unlockProgress + this.score;

        this.nextUnlockCost = UnlockTracker.incrementUnlockRamp(this.unlockCost);

        if (this.unlockTargetProgress >= this.unlockCost) {
            this.unlockBundle = UnlockTracker.getUnlockBundle(AbstractDungeon.player.chosenClass, this.unlockLevel);

            if (this.unlockLevel == 4) {
                this.unlockTargetProgress = this.unlockCost;
            } else if (this.unlockTargetProgress > this.unlockCost - this.unlockProgress + this.nextUnlockCost - 1.0F) {

                this.unlockTargetProgress = this.unlockCost - this.unlockProgress + this.nextUnlockCost - 1.0F;
            }
        }

        logger.info("SCOR: " + this.score);
        logger.info("PROG: " + this.unlockProgress);
        logger.info("STRT: " + this.unlockTargetStart);
        logger.info("TRGT: " + this.unlockTargetProgress);
        logger.info("COST: " + this.unlockCost);

        UnlockTracker.addScore(AbstractDungeon.player.chosenClass, this.score);
        this.progressPercent = this.unlockTargetStart / this.unlockCost;
    }

    public static boolean shouldUploadMetricData() {
        return (Settings.UPLOAD_DATA && CardCrawlGame.publisherIntegration.isInitialized() && Settings.isStandardRun());
    }

    protected void submitVictoryMetrics() {
    }

    protected boolean canUploadLeaderboards() {
        if (Settings.isModded || Settings.isTrial || Settings.seedSet) {
            return false;
        }

        return true;
    }

    protected void uploadToSteamLeaderboards() {
        if (!canUploadLeaderboards()) {
            return;
        }

        uploadScoreHelper(AbstractDungeon.player.getLeaderboardCharacterName());
        StatsScreen.updateHighestScore(this.score);
    }

    protected void uploadScoreHelper(String characterString) {
        StringBuilder highScoreString = new StringBuilder();
        StringBuilder fastestWinString = new StringBuilder(characterString);

        if (Settings.isDailyRun) {
            highScoreString.append("DAILY_" + Long.toString(Settings.dailyDate));
            long lastDaily = Settings.dailyPref.getLong("LAST_DAILY", 0L);

            Settings.hasDoneDailyToday = (lastDaily == Settings.dailyDate);
            if (Settings.hasDoneDailyToday) {
                logger.info("Player has already done the daily for: " + Settings.dailyDate);
            }
        } else {
            highScoreString.append(characterString);
            highScoreString.append("_HIGH_SCORE");
        }
        fastestWinString.append("_FASTEST_WIN");

        if (Settings.isBeta) {
            highScoreString.append("_BETA");
            fastestWinString.append("_BETA");
        }

        if (Settings.isDailyRun && !Settings.hasDoneDailyToday) {
            logger.info("Uploading score for day: " + Settings.dailyDate + "\nScore is: " + this.score);
            Settings.dailyPref.putLong("LAST_DAILY", Settings.dailyDate);
            Settings.dailyPref.flush();
            CardCrawlGame.publisherIntegration.uploadDailyLeaderboardScore(highScoreString.toString(), this.score);
        } else if (!Settings.isDailyRun) {
            CardCrawlGame.publisherIntegration.uploadLeaderboardScore(highScoreString.toString(), this.score);
        }

        if (isVictory && this.playtime < 18000L && this.playtime > 280L && !Settings.isDailyRun) {
            CardCrawlGame.publisherIntegration.uploadLeaderboardScore(fastestWinString
                    .toString(),
                    Math.toIntExact(this.playtime));
        }
    }

    public static int calcScore(boolean victory) {
        floorPoints = 0;
        monsterPoints = 0;
        elite1Points = 0;
        elite2Points = 0;
        elite3Points = 0;
        bossPoints = 0;
        ascensionPoints = 0;

        int tmp = AbstractDungeon.floorNum * 5;
        floorPoints = AbstractDungeon.floorNum * 5;
        monsterPoints = CardCrawlGame.monstersSlain * 2;
        elite1Points = CardCrawlGame.elites1Slain * 10;
        elite2Points = CardCrawlGame.elites2Slain * 20;
        elite3Points = CardCrawlGame.elites3Slain * 30;

        bossPoints = 0;
        int bossMultiplier = 50;
        for (int i = 0; i < AbstractDungeon.bossCount; i++) {
            bossPoints += bossMultiplier;
            bossMultiplier += 50;
        }

        tmp += monsterPoints;
        tmp += elite1Points;
        tmp += elite2Points;
        tmp += elite3Points;
        tmp += bossPoints;

        tmp += CardCrawlGame.champion * 25;
        if (CardCrawlGame.perfect >= 3) {
            tmp += 200;
        } else {
            tmp += CardCrawlGame.perfect * 50;
        }

        if (CardCrawlGame.overkill) {
            tmp += 25;
        }

        if (CardCrawlGame.combo) {
            tmp += 25;
        }

        if (AbstractDungeon.isAscensionMode) {
            ascensionPoints = MathUtils.round(tmp * 0.05F * AbstractDungeon.ascensionLevel);
            tmp += ascensionPoints;
        }

        tmp += checkScoreBonus(victory);

        return tmp;
    }

    protected static int checkScoreBonus(boolean victory) {
        int points = 0;

        if (AbstractDungeon.player.hasRelic("Spirit Poop")) {
            IS_POOPY = true;
            points--;
        }

        IS_FULL_SET = AbstractDungeon.player.masterDeck.fullSetCheck();
        if (IS_FULL_SET > 0) {
            points += 25 * IS_FULL_SET;
        }

        if (AbstractDungeon.player.relics.size() >= 25) {
            IS_SHINY = true;
            points += 50;
        }

        if (AbstractDungeon.player.masterDeck.size() >= 50) {
            IS_ENCYCLOPEDIA = true;
            points += 50;
        } else if (AbstractDungeon.player.masterDeck.size() >= 35) {
            IS_LIBRARY = true;
            points += 25;
        }

        int tmpDiff = AbstractDungeon.player.maxHealth - AbstractDungeon.player.startingMaxHP;
        if (tmpDiff >= 30) {
            IS_STUFFED = true;
            points += 50;
        } else if (tmpDiff >= 15) {
            IS_WELL_FED = true;
            points += 25;
        }

        if (AbstractDungeon.player.masterDeck.cursedCheck()) {
            IS_CURSES = true;
            points += 100;
        }

        if (CardCrawlGame.goldGained >= 3000) {
            IS_I_LIKE_GOLD = true;
            points += 75;
        } else if (CardCrawlGame.goldGained >= 2000) {
            IS_RAINING_MONEY = true;
            points += 50;
        } else if (CardCrawlGame.goldGained >= 1000) {
            IS_MONEY_MONEY = true;
            points += 25;
        }

        if (CardCrawlGame.mysteryMachine >= 15) {
            IS_MYSTERY_MACHINE = true;
            points += 25;
        }

        if (victory) {

            logger.info("PLAYTIME: " + CardCrawlGame.playtime);
            if ((long) CardCrawlGame.playtime <= 2700L) {
                IS_LIGHT_SPEED = true;
                points += 50;
            } else if ((long) CardCrawlGame.playtime <= 3600L) {
                IS_SPEEDSTER = true;
                points += 25;
            }

            if (AbstractDungeon.player.masterDeck.highlanderCheck()) {
                IS_HIGHLANDER = true;
                points += 100;
            }

            if (AbstractDungeon.player.masterDeck.pauperCheck()) {
                IS_PAUPER = true;
                points += 50;
            }

            if (isVictory && CardCrawlGame.dungeon instanceof com.megacrit.cardcrawl.dungeons.TheEnding) {
                points += 250;
            }
        }

        return points;
    }

    protected void renderStatsScreen(SpriteBatch sb) {
        if (this.showingStats) {
            this.fadeBgColor.a = (1.0F - this.statsTimer) * 0.6F;
            sb.setColor(this.fadeBgColor);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0.0F, 0.0F, Settings.WIDTH, Settings.HEIGHT);

            float y = STAT_START_Y + this.stats.size() * STAT_OFFSET_Y / 2.0F;
            if (this.stats.size() >= 10) {
                y = STAT_START_Y + (this.stats.size() / 2) * STAT_OFFSET_Y / 2.0F;
            }

            for (int i = 0; i < this.stats.size(); i++) {

                if (this.stats.size() <= 10) {
                    if (i == this.stats.size() - 2) {
                        ((GameOverStat) this.stats.get(i)).renderLine(sb, false, y);
                    } else {
                        ((GameOverStat) this.stats.get(i)).render(sb, Settings.WIDTH / 2.0F - 220.0F * Settings.scale,
                                y);
                    }

                } else if (i != this.stats.size() - 1) {
                    if (i < (this.stats.size() - 1) / 2) {
                        ((GameOverStat) this.stats.get(i)).render(sb, 440.0F * Settings.xScale, y);
                    } else {
                        ((GameOverStat) this.stats.get(i)).render(sb, 1050.0F * Settings.xScale,
                                y + STAT_OFFSET_Y * ((this.stats

                                        .size() - 1) / 2));
                    }
                } else {

                    ((GameOverStat) this.stats.get(i)).renderLine(sb, true,
                            y + STAT_OFFSET_Y * (this.stats.size() / 2));

                    ((GameOverStat) this.stats.get(i)).render(sb, 740.0F * Settings.xScale,
                            y + STAT_OFFSET_Y * (this.stats.size() / 2 - 1));
                }
                y -= STAT_OFFSET_Y;
            }

            renderProgressBar(sb);
        }
    }

    protected void renderProgressBar(SpriteBatch sb) {
        if (this.maxLevel) {
            return;
        }

        this.whiteUiColor.a = this.progressBarAlpha * 0.3F;
        sb.setColor(this.whiteUiColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, this.progressBarX, Settings.HEIGHT * 0.2F, this.progressBarWidth,
                14.0F * Settings.scale);

        sb.setColor(new Color(1.0F, 0.8F, 0.3F, this.progressBarAlpha * 0.9F));
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, this.progressBarX, Settings.HEIGHT * 0.2F,
                this.progressBarWidth * this.progressPercent, 14.0F * Settings.scale);

        sb.setColor(new Color(0.0F, 0.0F, 0.0F, this.progressBarAlpha * 0.25F));
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, this.progressBarX, Settings.HEIGHT * 0.2F,
                this.progressBarWidth * this.progressPercent, 4.0F * Settings.scale);

        String derp = "[" + (int) this.unlockProgress + "/" + this.unlockCost + "]";
        this.creamUiColor.a = this.progressBarAlpha * 0.9F;

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.topPanelInfoFont, derp, 576.0F * Settings.xScale,
                Settings.HEIGHT * 0.2F - 12.0F * Settings.scale, this.creamUiColor);

        if (5 - this.unlockLevel == 1) {
            derp = TEXT[42] + (5 - this.unlockLevel);
        } else {
            derp = TEXT[41] + (5 - this.unlockLevel);
        }

        FontHelper.renderFontRightTopAligned(sb, FontHelper.topPanelInfoFont, derp, 1344.0F * Settings.xScale,
                Settings.HEIGHT * 0.2F - 12.0F * Settings.scale, this.creamUiColor);
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\
 * GameOverScreen.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


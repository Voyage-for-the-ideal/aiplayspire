package com.megacrit.cardcrawl.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.SaveHelper;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.controller.CInputHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.metrics.Metrics;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.rooms.CampfireUI;
import com.megacrit.cardcrawl.rooms.RestRoom;
import com.megacrit.cardcrawl.saveAndContinue.SaveAndContinue;
import com.megacrit.cardcrawl.screens.stats.StatsScreen;
import com.megacrit.cardcrawl.ui.buttons.DynamicBanner;
import com.megacrit.cardcrawl.ui.buttons.ReturnToMenuButton;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.AscensionLevelUpTextEffect;
import com.megacrit.cardcrawl.vfx.AscensionUnlockedTextEffect;
import com.megacrit.cardcrawl.vfx.BetaCardArtUnlockedTextEffect;
import com.megacrit.cardcrawl.vfx.scene.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;

public class VictoryScreen
        extends GameOverScreen {
    private static final Logger logger = LogManager.getLogger(VictoryScreen.class.getName());
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("VictoryScreen");
    public static final String[] TEXT = uiStrings.TEXT;

    private MonsterGroup monsters;
    private ArrayList<AbstractGameEffect> effect = new ArrayList<>();
    private float effectTimer = 0.0F;
    private float effectTimer2 = 0.0F;
    public static long STINGER_ID = -999L;
    public static String STINGER_KEY = "";

    private int unlockedBetaArt = 0;

    public VictoryScreen(MonsterGroup m) {
        isVictory = true;
        this.playtime = (long) CardCrawlGame.playtime;

        if (this.playtime < 0L) {
            this.playtime = 0L;
        }

        AbstractDungeon.getCurrRoom().clearEvent();
        resetScoreChecks();

        AbstractDungeon.is_victory = true;
        AbstractDungeon.player.drawX = Settings.WIDTH / 2.0F;
        AbstractDungeon.dungeonMapScreen.closeInstantly();
        AbstractDungeon.screen = AbstractDungeon.CurrentScreen.VICTORY;
        AbstractDungeon.overlayMenu.showBlackScreen(1.0F);
        AbstractDungeon.previousScreen = null;
        AbstractDungeon.overlayMenu.cancelButton.hideInstantly();
        AbstractDungeon.isScreenUp = true;
        this.monsters = m;
        logger.info("PLAYTIME: " + this.playtime);

        if (AbstractDungeon.getCurrRoom() instanceof RestRoom) {
            ((RestRoom) AbstractDungeon.getCurrRoom()).cutFireSound();
        }

        this.showingStats = false;
        this.returnButton = new ReturnToMenuButton();
        this.returnButton.appear(Settings.WIDTH / 2.0F, Settings.HEIGHT * 0.15F, TEXT[0]);

        AbstractDungeon.dynamicBanner.appear(TEXT[12]);

        if (Settings.isStandardRun()) {
            CardCrawlGame.playerPref.putInteger(AbstractDungeon.player.chosenClass.name() + "_SPIRITS", 1);
        }

        this.unlockedBetaArt = -1;
        switch (AbstractDungeon.player.chosenClass) {
            case IRONCLAD:
                if (!UnlockTracker.isAchievementUnlocked("RUBY_PLUS")) {
                    this.unlockedBetaArt = 0;
                    UnlockTracker.unlockAchievement("RUBY_PLUS");
                }
                break;
            case THE_SILENT:
                if (!UnlockTracker.isAchievementUnlocked("EMERALD_PLUS")) {
                    this.unlockedBetaArt = 1;
                    UnlockTracker.unlockAchievement("EMERALD_PLUS");
                    CampfireUI.hidden = true;
                }
                break;
            case DEFECT:
                if (!UnlockTracker.isAchievementUnlocked("SAPPHIRE_PLUS")) {
                    this.unlockedBetaArt = 2;
                    UnlockTracker.unlockAchievement("SAPPHIRE_PLUS");
                    this.effectTimer2 = 5.0F;
                }
                break;
            case WATCHER:
                if (!UnlockTracker.isAchievementUnlocked("AMETHYST_PLUS")) {
                    this.unlockedBetaArt = 4;
                    UnlockTracker.unlockAchievement("AMETHYST");
                    UnlockTracker.unlockAchievement("AMETHYST_PLUS");
                }
                break;
        }

        if (UnlockTracker.isAchievementUnlocked("RUBY_PLUS") && UnlockTracker.isAchievementUnlocked("EMERALD_PLUS") &&
                UnlockTracker.isAchievementUnlocked("SAPPHIRE_PLUS")) {
            UnlockTracker.unlockAchievement("THE_ENDING");
        }

        submitVictoryMetrics();

        if (this.playtime != 0L) {
            StatsScreen.updateVictoryTime(this.playtime);
        }

        StatsScreen.incrementVictory(AbstractDungeon.player.getCharStat());
        StatsScreen.incrementAscension(AbstractDungeon.player.getCharStat());

        if (AbstractDungeon.ascensionLevel == 10 && !Settings.isTrial) {
            UnlockTracker.unlockAchievement("ASCEND_10");
        } else if (AbstractDungeon.ascensionLevel == 20 && !Settings.isTrial) {
            UnlockTracker.unlockAchievement("ASCEND_20");
        }

        if (this.playtime != 0L) {
            StatsScreen.incrementPlayTime(this.playtime);
        }

        if (Settings.isStandardRun()) {
            StatsScreen.updateFurthestAscent(AbstractDungeon.floorNum);
        } else if (Settings.isDailyRun) {
            StatsScreen.updateHighestDailyScore(AbstractDungeon.floorNum);
        }

        if (SaveHelper.shouldDeleteSave()) {
            SaveAndContinue.deleteSave(AbstractDungeon.player);
        }

        calculateUnlockProgress();
        if (!Settings.isEndless) {
            uploadToSteamLeaderboards();
        }
        createGameOverStats();
        CardCrawlGame.playerPref.flush();
    }

    private void createGameOverStats() {
        this.stats.clear();

        this.stats.add(new GameOverStat(TEXT[1] + " (" + AbstractDungeon.floorNum + ")", null,
                Integer.toString(floorPoints)));

        this.stats.add(new GameOverStat(TEXT[8] + " (" + CardCrawlGame.monstersSlain + ")", null,

                Integer.toString(monsterPoints)));

        this.stats.add(new GameOverStat(EXORDIUM_ELITE.NAME + " (" + CardCrawlGame.elites1Slain + ")", null,

                Integer.toString(elite1Points)));

        if (Settings.isEndless) {
            if (CardCrawlGame.elites2Slain > 0) {
                this.stats.add(new GameOverStat(CITY_ELITE.NAME + " (" + CardCrawlGame.elites2Slain + ")", null,

                        Integer.toString(elite2Points)));
            }
        } else if (CardCrawlGame.dungeon instanceof com.megacrit.cardcrawl.dungeons.TheCity
                || CardCrawlGame.dungeon instanceof com.megacrit.cardcrawl.dungeons.TheBeyond
                || CardCrawlGame.dungeon instanceof com.megacrit.cardcrawl.dungeons.TheEnding) {

            this.stats.add(new GameOverStat(CITY_ELITE.NAME + " (" + CardCrawlGame.elites2Slain + ")", null,

                    Integer.toString(elite2Points)));
        }

        if (Settings.isEndless) {
            if (CardCrawlGame.elites3Slain > 0) {
                this.stats.add(new GameOverStat(BEYOND_ELITE.NAME + " (" + CardCrawlGame.elites3Slain + ")", null,

                        Integer.toString(elite3Points)));
            }
        } else if (CardCrawlGame.dungeon instanceof com.megacrit.cardcrawl.dungeons.TheBeyond
                || CardCrawlGame.dungeon instanceof com.megacrit.cardcrawl.dungeons.TheEnding) {
            this.stats.add(new GameOverStat(BEYOND_ELITE.NAME + " (" + CardCrawlGame.elites3Slain + ")", null,

                    Integer.toString(elite3Points)));
        }

        this.stats.add(new GameOverStat(BOSSES_SLAIN.NAME + " (" + AbstractDungeon.bossCount + ")", null,

                Integer.toString(bossPoints)));

        if (IS_POOPY) {
            this.stats.add(new GameOverStat(POOPY.NAME, POOPY.DESCRIPTIONS[0], Integer.toString(-1)));
        }
        if (IS_SPEEDSTER) {
            this.stats.add(new GameOverStat(SPEEDSTER.NAME, SPEEDSTER.DESCRIPTIONS[0], Integer.toString(25)));
        }
        if (IS_LIGHT_SPEED) {
            this.stats.add(new GameOverStat(LIGHT_SPEED.NAME, LIGHT_SPEED.DESCRIPTIONS[0],
                    Integer.toString(50)));
        }
        if (IS_HIGHLANDER) {
            this.stats.add(new GameOverStat(HIGHLANDER.NAME, HIGHLANDER.DESCRIPTIONS[0],
                    Integer.toString(100)));
        }
        if (IS_SHINY) {
            this.stats.add(new GameOverStat(SHINY.NAME, SHINY.DESCRIPTIONS[0], Integer.toString(50)));
        }

        if (IS_I_LIKE_GOLD) {
            this.stats.add(new GameOverStat(I_LIKE_GOLD.NAME + " (" + CardCrawlGame.goldGained + ")",
                    I_LIKE_GOLD.DESCRIPTIONS[0],

                    Integer.toString(75)));
        } else if (IS_RAINING_MONEY) {
            this.stats.add(new GameOverStat(RAINING_MONEY.NAME + " (" + CardCrawlGame.goldGained + ")",
                    RAINING_MONEY.DESCRIPTIONS[0],

                    Integer.toString(50)));
        } else if (IS_MONEY_MONEY) {
            this.stats.add(new GameOverStat(MONEY_MONEY.NAME + " (" + CardCrawlGame.goldGained + ")",
                    MONEY_MONEY.DESCRIPTIONS[0],

                    Integer.toString(25)));
        }

        if (IS_MYSTERY_MACHINE) {
            this.stats.add(new GameOverStat(MYSTERY_MACHINE.NAME + " (" + CardCrawlGame.mysteryMachine + ")",
                    MYSTERY_MACHINE.DESCRIPTIONS[0],

                    Integer.toString(25)));
        }

        if (IS_FULL_SET > 0) {
            this.stats.add(new GameOverStat(COLLECTOR.NAME + " (" + IS_FULL_SET + ")", COLLECTOR.DESCRIPTIONS[0],

                    Integer.toString(25 * IS_FULL_SET)));
        }

        if (IS_PAUPER) {
            this.stats.add(new GameOverStat(PAUPER.NAME, PAUPER.DESCRIPTIONS[0], Integer.toString(50)));
        }
        if (IS_LIBRARY) {
            this.stats.add(new GameOverStat(LIBRARIAN.NAME, LIBRARIAN.DESCRIPTIONS[0], Integer.toString(25)));
        }
        if (IS_ENCYCLOPEDIA) {
            this.stats.add(new GameOverStat(ENCYCLOPEDIAN.NAME, ENCYCLOPEDIAN.DESCRIPTIONS[0],

                    Integer.toString(50)));
        }

        if (IS_STUFFED) {
            this.stats.add(new GameOverStat(STUFFED.NAME, STUFFED.DESCRIPTIONS[0], Integer.toString(50)));
        } else if (IS_WELL_FED) {
            this.stats.add(new GameOverStat(WELL_FED.NAME, WELL_FED.DESCRIPTIONS[0], Integer.toString(25)));
        }

        if (IS_CURSES) {
            this.stats.add(new GameOverStat(CURSES.NAME, CURSES.DESCRIPTIONS[0], Integer.toString(100)));
        }

        if (IS_ON_MY_OWN) {
            this.stats.add(new GameOverStat(ON_MY_OWN_TERMS.NAME, ON_MY_OWN_TERMS.DESCRIPTIONS[0],

                    Integer.toString(50)));
        }

        if (CardCrawlGame.champion > 0) {
            this.stats
                    .add(new GameOverStat(CHAMPION.NAME + " (" + CardCrawlGame.champion + ")", CHAMPION.DESCRIPTIONS[0],

                            Integer.toString(25 * CardCrawlGame.champion)));
        }

        if (CardCrawlGame.perfect >= 3) {
            this.stats.add(new GameOverStat(BEYOND_PERFECT.NAME, BEYOND_PERFECT.DESCRIPTIONS[0],

                    Integer.toString(200)));
        } else if (CardCrawlGame.perfect > 0) {
            this.stats.add(new GameOverStat(PERFECT.NAME + " (" + CardCrawlGame.perfect + ")", PERFECT.DESCRIPTIONS[0],

                    Integer.toString(50 * CardCrawlGame.perfect)));
        }

        if (CardCrawlGame.overkill) {
            this.stats.add(new GameOverStat(OVERKILL.NAME, OVERKILL.DESCRIPTIONS[0], Integer.toString(25)));
        }

        if (CardCrawlGame.combo) {
            this.stats.add(new GameOverStat(COMBO.NAME, COMBO.DESCRIPTIONS[0], Integer.toString(25)));
        }

        if (AbstractDungeon.isAscensionMode) {
            this.stats.add(new GameOverStat(ASCENSION.NAME + " (" + AbstractDungeon.ascensionLevel + ")",
                    ASCENSION.DESCRIPTIONS[0],

                    Integer.toString(ascensionPoints)));
        }

        if (CardCrawlGame.dungeon instanceof com.megacrit.cardcrawl.dungeons.TheEnding) {
            this.stats.add(new GameOverStat(HEARTBREAKER.NAME, HEARTBREAKER.DESCRIPTIONS[0],

                    Integer.toString(250)));
        }

        this.stats.add(new GameOverStat());

        this.stats.add(new GameOverStat(TEXT[4], null, Integer.toString(this.score)));
    }

    protected void submitVictoryMetrics() {
        Metrics metrics = new Metrics();

        metrics.gatherAllDataAndSave(false, true, null);

        if (Settings.UPLOAD_DATA) {
            metrics.setValues(false, true, null, Metrics.MetricRequestType.UPLOAD_METRICS);
            Thread t = new Thread((Runnable) metrics);
            t.start();
        }

        if (Settings.isStandardRun()) {
            StatsScreen.updateFurthestAscent(AbstractDungeon.floorNum);
        }

        if (SaveHelper.shouldDeleteSave()) {
            SaveAndContinue.deleteSave(AbstractDungeon.player);
        }
    }

    public void hide() {
        this.returnButton.hide();
        AbstractDungeon.dynamicBanner.hide();
    }

    public void reopen() {
        reopen(false);
    }

    public void reopen(boolean fromVictoryUnlock) {
        AbstractDungeon.previousScreen = AbstractDungeon.CurrentScreen.DEATH;
        this.statsTimer = 0.5F;

        AbstractDungeon.dynamicBanner.appearInstantly(TEXT[12]);
        AbstractDungeon.overlayMenu.showBlackScreen(1.0F);

        if (fromVictoryUnlock) {
            this.returnButton.appear(Settings.WIDTH / 2.0F, Settings.HEIGHT * 0.15F, TEXT[0]);
        } else if (!this.showingStats) {
            this.returnButton.appear(Settings.WIDTH / 2.0F, Settings.HEIGHT * 0.15F, TEXT[0]);
        } else if (this.unlockBundle == null) {
            this.returnButton.appear(Settings.WIDTH / 2.0F, Settings.HEIGHT * 0.15F, TEXT[0]);
        } else {
            this.returnButton.appear(Settings.WIDTH / 2.0F, Settings.HEIGHT * 0.15F, TEXT[5]);
        }
    }

    public void update() {
        if (Settings.isDebug && InputHelper.justClickedRight) {
            UnlockTracker.resetUnlockProgress(AbstractDungeon.player.chosenClass);
        }

        updateControllerInput();
        this.returnButton.update();
        if (this.returnButton.hb.clicked || (this.returnButton.show && CInputActionSet.select.isJustPressed())) {
            CInputActionSet.topPanel.unpress();
            if (Settings.isControllerMode) {
                Gdx.input.setCursorPosition(10, Settings.HEIGHT / 2);
            }
            this.returnButton.hb.clicked = false;
            if (!this.showingStats) {
                this.showingStats = true;
                this.statsTimer = 0.5F;
                logger.info("Clicked");

                this.returnButton = new ReturnToMenuButton();

                updateAscensionAndBetaArtProgress();

                if (this.unlockBundle == null) {
                    this.returnButton.appear(Settings.WIDTH / 2.0F, Settings.HEIGHT * 0.15F, TEXT[0]);
                } else {
                    this.returnButton.appear(Settings.WIDTH / 2.0F, Settings.HEIGHT * 0.15F, TEXT[5]);
                }
            } else if (this.unlockBundle != null) {
                AbstractDungeon.gUnlockScreen.open(this.unlockBundle, true);
                AbstractDungeon.previousScreen = AbstractDungeon.CurrentScreen.VICTORY;
                AbstractDungeon.screen = AbstractDungeon.CurrentScreen.NEOW_UNLOCK;
                this.unlockBundle = null;
                this.returnButton.label = TEXT[5];
            } else {
                this.returnButton.hide();

                if (AbstractDungeon.unlocks.isEmpty() || Settings.isDemo) {
                    CardCrawlGame.playCreditsBgm = true;
                    CardCrawlGame.startOverButShowCredits();
                } else {
                    AbstractDungeon.unlocks.clear();
                    Settings.isTrial = false;
                    Settings.isDailyRun = false;
                    Settings.isEndless = false;
                    CardCrawlGame.trial = null;

                    if (Settings.isDailyRun) {
                        CardCrawlGame.startOver();
                    } else {
                        CardCrawlGame.playCreditsBgm = true;
                        CardCrawlGame.startOverButShowCredits();
                    }
                }
            }
        }

        updateStatsScreen();

        if (this.monsters != null) {
            this.monsters.update();
            this.monsters.updateAnimations();
        }

        updateVfx();
    }

    private void updateControllerInput() {
        if (!Settings.isControllerMode || AbstractDungeon.topPanel.selectPotionMode
                || !AbstractDungeon.topPanel.potionUi.isHidden || AbstractDungeon.player.viewingRelics) {
            return;
        }

        boolean anyHovered = false;
        int index = 0;
        if (this.stats != null) {
            for (GameOverStat s : this.stats) {
                if (s.hb.hovered) {
                    anyHovered = true;
                    break;
                }
                index++;
            }
        }

        if (!anyHovered) {
            index = -1;
        }

        if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
            index--;
            if (this.stats.size() > 10) {
                int numItemsInRightColumn = (this.stats.size() - 2) / 2;
                if (this.stats.size() % 2 == 0) {
                    numItemsInRightColumn--;
                }
                if (index == numItemsInRightColumn) {
                    index = this.stats.size() - 1;
                } else if (index < 0) {
                    index = this.stats.size() - 1;
                } else if (index == this.stats.size() - 2) {
                    index--;
                }

            } else if (index < 0) {
                index = this.stats.size() - 1;
            } else if (index == this.stats.size() - 2) {
                index--;
            }

            CInputHelper.setCursor(((GameOverStat) this.stats.get(index)).hb);
        } else if (CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) {
            if (index == -1) {
                index = 0;
                CInputHelper.setCursor(((GameOverStat) this.stats.get(index)).hb);

                return;
            }
            index++;
            if (this.stats.size() > 10) {
                int numItemsInLeftColumn = (this.stats.size() - 2) / 2;
                if (this.stats.size() % 2 != 0) {
                    numItemsInLeftColumn++;
                }
                if (index == numItemsInLeftColumn) {
                    index = this.stats.size() - 1;
                }
            } else {
                if (index > this.stats.size() - 1) {
                    index = 0;
                }
                if (index == this.stats.size() - 2) {
                    index++;
                }
            }

            if (index > this.stats.size() - 3) {
                index = this.stats.size() - 1;
            }
            CInputHelper.setCursor(((GameOverStat) this.stats.get(index)).hb);
        } else if (CInputActionSet.left.isJustPressed() || CInputActionSet.altLeft.isJustPressed()
                || CInputActionSet.right
                        .isJustPressed()
                || CInputActionSet.altRight.isJustPressed()) {
            if (this.stats.size() > 10) {
                int numItemsInLeftColumn = (this.stats.size() - 2) / 2;

                if (this.stats.size() % 2 != 0) {
                    numItemsInLeftColumn++;
                }

                if (index < numItemsInLeftColumn - 1) {
                    index += numItemsInLeftColumn;
                } else if (index == numItemsInLeftColumn - 1) {
                    if (this.stats.size() % 2 != 0) {
                        index += numItemsInLeftColumn - 1;
                    } else {
                        index += numItemsInLeftColumn;
                    }
                } else if (index >= numItemsInLeftColumn && index < this.stats.size() - 2) {
                    index -= numItemsInLeftColumn;
                }
            }
            if (index > this.stats.size() - 1) {
                index = this.stats.size() - 1;
            }
            if (index != -1) {
                CInputHelper.setCursor(((GameOverStat) this.stats.get(index)).hb);
            }
        }
    }

    private void updateAscensionAndBetaArtProgress() {
        if (AbstractDungeon.isAscensionMode && !Settings.seedSet && !Settings.isTrial
                && AbstractDungeon.ascensionLevel < 20 &&

                StatsScreen.isPlayingHighestAscension(AbstractDungeon.player.getPrefs())) {
            StatsScreen.incrementAscension(AbstractDungeon.player.getCharStat());
            AbstractDungeon.topLevelEffects.add(new AscensionLevelUpTextEffect());
        } else if (!AbstractDungeon.ascensionCheck && UnlockTracker.isAscensionUnlocked(AbstractDungeon.player)) {
            AbstractDungeon.topLevelEffects.add(new AscensionUnlockedTextEffect());
        } else if (this.unlockedBetaArt != -1) {
            AbstractDungeon.topLevelEffects.add(new BetaCardArtUnlockedTextEffect(this.unlockedBetaArt));
        }
    }
    private void updateVfx() {
        boolean foundEyeVfx;
        boolean createdEffect;
        switch (AbstractDungeon.player.chosenClass) {
            case IRONCLAD:
                this.effectTimer -= Gdx.graphics.getDeltaTime();
                if (this.effectTimer < 0.0F) {
                    this.effect.add(new SlowFireParticleEffect());
                    this.effect.add(new SlowFireParticleEffect());
                    this.effect.add(new IroncladVictoryFlameEffect());
                    this.effect.add(new IroncladVictoryFlameEffect());
                    this.effect.add(new IroncladVictoryFlameEffect());
                    this.effectTimer = 0.1F;
                }
                break;
            case THE_SILENT:
                this.effectTimer -= Gdx.graphics.getDeltaTime();
                if (this.effectTimer < 0.0F) {
                    if (this.effect.size() < 100) {
                        this.effect.add(new SilentVictoryStarEffect());
                        this.effect.add(new SilentVictoryStarEffect());
                        this.effect.add(new SilentVictoryStarEffect());
                        this.effect.add(new SilentVictoryStarEffect());
                    }
                    this.effectTimer = 0.1F;
                }
                this.effectTimer2 += Gdx.graphics.getDeltaTime();
                if (this.effectTimer2 > 1.0F) {
                    this.effectTimer2 = 1.0F;
                }
                break;
            case DEFECT:
                foundEyeVfx = false;
                for (AbstractGameEffect e : this.effect) {
                    if (e instanceof DefectVictoryEyesEffect) {
                        foundEyeVfx = true;

                        break;
                    }
                }
                if (!foundEyeVfx) {
                    this.effect.add(new DefectVictoryEyesEffect());
                }

                if (this.effect.size() < 15) {
                    this.effect.add(new DefectVictoryNumberEffect());
                }
                break;
            case WATCHER:
                createdEffect = false;
                for (AbstractGameEffect e : this.effect) {
                    if (e instanceof WatcherVictoryEffect) {
                        createdEffect = true;
                        break;
                    }
                }
                if (!createdEffect) {
                    this.effect.add(new WatcherVictoryEffect());
                }
                break;
        }
    }

    private void updateStatsScreen() {
        if (this.showingStats) {
            this.progressBarAlpha = MathHelper.slowColorLerpSnap(this.progressBarAlpha, 1.0F);

            this.statsTimer -= Gdx.graphics.getDeltaTime();
            if (this.statsTimer < 0.0F) {
                this.statsTimer = 0.0F;
            }

            this.returnButton.y = Interpolation.pow3In.apply(Settings.HEIGHT * 0.1F, Settings.HEIGHT * 0.15F,
                    this.statsTimer * 1.0F / 0.5F);

            AbstractDungeon.dynamicBanner.y = Interpolation.pow3In.apply(
                    Settings.HEIGHT / 2.0F + 320.0F * Settings.scale, DynamicBanner.Y, this.statsTimer * 1.0F / 0.5F);

            for (GameOverStat i : this.stats) {
                i.update();
            }

            if (this.statAnimateTimer < 0.0F) {
                boolean allStatsShown = true;

                for (GameOverStat i : this.stats) {
                    if (i.hidden) {
                        i.hidden = false;
                        this.statAnimateTimer = 0.1F;
                        allStatsShown = false;

                        break;
                    }
                }
                if (allStatsShown) {
                    animateProgressBar();
                }
            } else {
                this.statAnimateTimer -= Gdx.graphics.getDeltaTime();
            }
        }
    }

    public void render(SpriteBatch sb) {
        sb.setBlendFunction(770, 1);
        Iterator<AbstractGameEffect> i;
        for (i = this.effect.iterator(); i.hasNext();) {
            AbstractGameEffect e = i.next();
            if (e.renderBehind) {
                e.render(sb);
            }
            e.update();
            if (e.isDone) {
                i.remove();
            }
        }
        sb.setBlendFunction(770, 771);

        if (AbstractDungeon.player.chosenClass == AbstractPlayer.PlayerClass.THE_SILENT) {
            sb.setColor(new Color(1.0F, 1.0F, 1.0F, this.effectTimer2));
            AbstractDungeon.player.renderShoulderImg(sb);
        }

        sb.setBlendFunction(770, 1);
        for (i = this.effect.iterator(); i.hasNext();) {
            AbstractGameEffect e = i.next();
            if (!e.renderBehind) {
                e.render(sb);
            }
        }

        sb.setBlendFunction(770, 771);

        renderStatsScreen(sb);
        this.returnButton.render(sb);
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\
 * VictoryScreen.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


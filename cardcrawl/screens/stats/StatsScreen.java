package com.megacrit.cardcrawl.screens.stats;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.Prefs;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.controller.CInputHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.AchievementStrings;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;
import com.megacrit.cardcrawl.screens.mainMenu.MenuCancelButton;
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBar;
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBarListener;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class StatsScreen
        implements ScrollBarListener {
    private static final Logger logger = LogManager.getLogger(StatsScreen.class.getName());
    private static final AchievementStrings achievementStrings = CardCrawlGame.languagePack
            .getAchievementString("StatsScreen");

    public static final String[] NAMES = achievementStrings.NAMES;
    public static final String[] TEXT = achievementStrings.TEXT;
    public MenuCancelButton button = new MenuCancelButton();

    public Hitbox allCharsHb = new Hitbox(150.0F * Settings.scale, 150.0F * Settings.scale);
    public Hitbox ironcladHb = new Hitbox(150.0F * Settings.scale, 150.0F * Settings.scale);
    public Hitbox silentHb;
    public Hitbox defectHb;
    public Hitbox watcherHb;
    public Hitbox controllerHb;
    public boolean screenUp = false;
    private static final float SHOW_X = 300.0F * Settings.scale;
    private static final float HIDE_X = -800.0F * Settings.scale;
    private float screenX = HIDE_X;
    private float targetX = HIDE_X;

    private boolean grabbedScreen = false;

    private float grabStartY = 0.0F;
    private float scrollTargetY = 0.0F;
    private float scrollY = 0.0F;
    private float scrollLowerBound = -Settings.DEFAULT_SCROLL_LIMIT;
    private float scrollUpperBound = Settings.DEFAULT_SCROLL_LIMIT;
    private ScrollBar scrollBar = null;

    public static CharStat all;

    public static AchievementGrid achievements;

    public static TextureAtlas atlas = null;

    public StatsScreen() {
        logger.info("Loading character stats.");
        CardCrawlGame.characterManager.refreshAllCharStats();
        all = new CharStat(CardCrawlGame.characterManager.getAllCharacterStats());
        achievements = new AchievementGrid();
        Settings.totalPlayTime = all.playTime;
    }

    public void refreshData() {
        logger.info("Refreshing stats screen data.");
        CardCrawlGame.characterManager.refreshAllCharStats();
        all = new CharStat(CardCrawlGame.characterManager.getAllCharacterStats());
        achievements = new AchievementGrid();
        Settings.totalPlayTime = all.playTime;
    }

    public void update() {
        updateControllerInput();
        if (Settings.isControllerMode && this.controllerHb != null) {
            if (Gdx.input.getY() > Settings.HEIGHT * 0.75F) {
                this.scrollTargetY += Settings.SCROLL_SPEED;
            } else if (Gdx.input.getY() < Settings.HEIGHT * 0.25F) {
                this.scrollTargetY -= Settings.SCROLL_SPEED;
            }
        }
        if (Settings.isControllerMode && this.controllerHb != null) {
            Gdx.input.setCursorPosition((int) this.controllerHb.cX, (int) (Settings.HEIGHT - this.controllerHb.cY));
        }

        this.button.update();

        if (this.button.hb.clicked || InputHelper.pressedEscape) {
            InputHelper.pressedEscape = false;
            CardCrawlGame.mainMenuScreen.screen = MainMenuScreen.CurScreen.MAIN_MENU;
            hide();
        }

        this.screenX = MathHelper.uiLerpSnap(this.screenX, this.targetX);
        boolean isDraggingScrollBar = this.scrollBar.update();
        if (!isDraggingScrollBar) {
            updateScrolling();
        }
        achievements.update();
    }

    private void updateControllerInput() {
        if (!Settings.isControllerMode) {
            return;
        }

        boolean anyHovered = false;
        int index = 0;

        this.allCharsHb.update();

        this.ironcladHb.update();
        if (this.silentHb != null) {
            this.silentHb.update();
        }
        if (this.defectHb != null) {
            this.defectHb.update();
        }
        if (this.watcherHb != null) {
            this.watcherHb.update();
        }

        if (this.allCharsHb != null &&
                this.allCharsHb.hovered) {
            anyHovered = true;
        }

        index = 0;
        if (!anyHovered) {
            for (AchievementItem a : achievements.items) {
                a.hb.update();
                if (a.hb.hovered) {
                    anyHovered = true;
                    break;
                }
                index++;
            }
        }

        if (!anyHovered) {
            if (this.ironcladHb != null && this.ironcladHb.hovered) {
                anyHovered = true;
            }
            if (this.silentHb != null && this.silentHb.hovered) {
                anyHovered = true;
            }
            if (this.defectHb != null && this.defectHb.hovered) {
                anyHovered = true;
            }
            if (this.watcherHb != null && this.watcherHb.hovered) {
                anyHovered = true;
            }
        }

        if (!anyHovered) {
            CInputHelper.setCursor(this.allCharsHb);
            this.controllerHb = this.allCharsHb;
        } else if (this.allCharsHb.hovered) {
            if (CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) {
                CInputHelper.setCursor(((AchievementItem) achievements.items.get(0)).hb);
                this.controllerHb = ((AchievementItem) achievements.items.get(0)).hb;
            }
        } else if (this.ironcladHb.hovered) {
            if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
                index = achievements.items.size() - achievements.items.size() % 5;
                CInputHelper.setCursor(((AchievementItem) achievements.items.get(index)).hb);
                this.controllerHb = ((AchievementItem) achievements.items.get(index)).hb;
            } else if ((CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed())
                    && this.silentHb != null) {

                CInputHelper.setCursor(this.silentHb);
                this.controllerHb = this.silentHb;
            }
        } else if (this.silentHb != null && this.silentHb.hovered) {
            if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
                CInputHelper.setCursor(this.ironcladHb);
                this.controllerHb = this.ironcladHb;
            } else if ((CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed())
                    && this.defectHb != null) {

                CInputHelper.setCursor(this.defectHb);
                this.controllerHb = this.defectHb;
            }
        } else if (this.defectHb != null && this.defectHb.hovered) {
            if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
                CInputHelper.setCursor(this.silentHb);
                this.controllerHb = this.silentHb;
            } else if ((CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed())
                    && this.watcherHb != null) {

                CInputHelper.setCursor(this.watcherHb);
                this.controllerHb = this.watcherHb;
            }
        } else if (this.watcherHb != null && this.watcherHb.hovered) {
            if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
                CInputHelper.setCursor(this.defectHb);
                this.controllerHb = this.defectHb;
            }

        } else if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
            index -= 5;
            if (index < 0) {
                CInputHelper.setCursor(this.allCharsHb);
                this.controllerHb = this.allCharsHb;
            } else {
                CInputHelper.setCursor(((AchievementItem) achievements.items.get(index)).hb);
                this.controllerHb = ((AchievementItem) achievements.items.get(index)).hb;
            }
        } else if (CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) {
            index += 5;
            if (index > achievements.items.size() - 1) {
                CInputHelper.setCursor(this.ironcladHb);
                this.controllerHb = this.ironcladHb;
            } else {
                CInputHelper.setCursor(((AchievementItem) achievements.items.get(index)).hb);
                this.controllerHb = ((AchievementItem) achievements.items.get(index)).hb;
            }
        } else if (CInputActionSet.left.isJustPressed() || CInputActionSet.altLeft.isJustPressed()) {
            index--;

            index += 5;
            if ((index % 5 == 4 || index == -1) && index > achievements.items.size() - 1) {
                index = achievements.items.size() - 1;
            }

            CInputHelper.setCursor(((AchievementItem) achievements.items.get(index)).hb);
            this.controllerHb = ((AchievementItem) achievements.items.get(index)).hb;
        } else if (CInputActionSet.right.isJustPressed() || CInputActionSet.altRight.isJustPressed()) {
            index++;
            if (index % 5 == 0) {
                index -= 5;
            }
            if (index > achievements.items.size() - 1) {
                index = achievements.items.size() - achievements.items.size() % 5;
            }
            CInputHelper.setCursor(((AchievementItem) achievements.items.get(index)).hb);
            this.controllerHb = ((AchievementItem) achievements.items.get(index)).hb;
        }
    }

    private void updateScrolling() {
        int y = InputHelper.mY;

        if (!this.grabbedScreen) {
            if (InputHelper.scrolledDown) {
                this.scrollTargetY += Settings.SCROLL_SPEED;
            } else if (InputHelper.scrolledUp) {
                this.scrollTargetY -= Settings.SCROLL_SPEED;
            }

            if (InputHelper.justClickedLeft) {
                this.grabbedScreen = true;
                this.grabStartY = y - this.scrollTargetY;
            }

        } else if (InputHelper.isMouseDown) {
            this.scrollTargetY = y - this.grabStartY;
        } else {
            this.grabbedScreen = false;
        }

        this.scrollY = MathHelper.scrollSnapLerpSpeed(this.scrollY, this.scrollTargetY);
        resetScrolling();
        updateBarPosition();
    }

    private void calculateScrollBounds() {
        if (!UnlockTracker.isCharacterLocked("Watcher")) {
            this.scrollUpperBound = 3400.0F * Settings.scale;
        } else if (!UnlockTracker.isCharacterLocked("Defect")) {
            this.scrollUpperBound = 3000.0F * Settings.scale;
        } else if (!UnlockTracker.isCharacterLocked("The Silent")) {
            this.scrollUpperBound = 2550.0F * Settings.scale;
        } else {
            this.scrollUpperBound = 2250.0F * Settings.scale;
        }
        this.scrollLowerBound = 100.0F * Settings.yScale;
    }

    private void resetScrolling() {
        if (this.scrollTargetY < this.scrollLowerBound) {
            this.scrollTargetY = MathHelper.scrollSnapLerpSpeed(this.scrollTargetY, this.scrollLowerBound);
        } else if (this.scrollTargetY > this.scrollUpperBound) {
            this.scrollTargetY = MathHelper.scrollSnapLerpSpeed(this.scrollTargetY, this.scrollUpperBound);
        }
    }

    public void open() {
        if (!Settings.isConsoleBuild) {
            if (CardCrawlGame.publisherIntegration.isInitialized() && CardCrawlGame.publisherIntegration
                    .getNumUnlockedAchievements() >= 45) {
                CardCrawlGame.publisherIntegration.unlockAchievement("ETERNAL_ONE");
            }

            retroactiveAmethystUnlock();
        }

        if (UnlockTracker.isAchievementUnlocked("RUBY_PLUS") && UnlockTracker.isAchievementUnlocked("EMERALD_PLUS") &&
                UnlockTracker.isAchievementUnlocked("SAPPHIRE_PLUS")) {
            UnlockTracker.unlockAchievement("THE_ENDING");
        }

        if (atlas == null) {
            atlas = new TextureAtlas(Gdx.files.internal("achievements/achievements.atlas"));
            logger.info("Loaded texture Achievement texture atlas.");
        }

        this.controllerHb = null;
        this.scrollTargetY = 0.0F;
        debugCharacterUnlock();

        this.targetX = SHOW_X;
        this.button.show(TEXT[0]);
        this.screenUp = true;
        CardCrawlGame.mainMenuScreen.screen = MainMenuScreen.CurScreen.STATS;

        if (this.scrollBar == null) {
            refreshData();
            calculateScrollBounds();
            this.scrollBar = new ScrollBar(this);
            logger.info("Creating new scrollbar for Stats Screen.");
        } else {
            achievements.refreshImg();
        }

        if (Settings.isControllerMode) {
            Gdx.input.setCursorPosition((int) this.allCharsHb.cX, Settings.HEIGHT - (int) this.allCharsHb.cY);
            this.controllerHb = this.allCharsHb;
        }

        debugAchievementUnlock();
    }

    private void debugAchievementUnlock() {
        if (Settings.isInfo) {
            for (AchievementItem i : achievements.items) {
                UnlockTracker.unlockAchievement(i.key);
            }
        }
    }

    private void debugCharacterUnlock() {
        if (Settings.isInfo) {
            for (String s : UnlockTracker.lockedCharacters) {
                UnlockTracker.hardUnlockOverride(s);
            }
        }
    }

    public void hide() {
        if (atlas != null) {
            atlas.dispose();
            atlas = null;
        }
        CardCrawlGame.sound.play("DECK_CLOSE", 0.1F);
        this.targetX = HIDE_X;
        this.button.hide();
        this.screenUp = false;
        CardCrawlGame.mainMenuScreen.panelScreen.refresh();
    }

    public static void updateFurthestAscent(int floor) {
        AbstractDungeon.player.getCharStat().furthestAscent(floor);
    }

    public static void updateHighestScore(int score) {
        AbstractDungeon.player.getCharStat().highestScore(score);
    }

    public static void updateHighestDailyScore(int score) {
        AbstractDungeon.player.getCharStat().highestDaily(score);
    }

    public static void updateVictoryTime(long time) {
        logger.info("Saving fastest victory...");
        AbstractDungeon.player.getCharStat().updateFastestVictory(time);
    }

    public static void incrementFloorClimbed() {
        AbstractDungeon.player.getCharStat().incrementFloorClimbed();
    }

    public static boolean isPlayingHighestAscension(Prefs p) {
        return (AbstractDungeon.ascensionLevel == p.getInteger("ASCENSION_LEVEL", 1));
    }

    public static void retroactiveAscend10Unlock(Prefs pref) {
        if (pref.getInteger("ASCENSION_LEVEL", 0) >= 11) {
            UnlockTracker.unlockAchievement("ASCEND_10");
        }
    }

    public static void retroactiveAscend20Unlock(Prefs pref) {
        if (pref.getInteger("ASCENSION_LEVEL", 0) >= 21) {
            UnlockTracker.unlockAchievement("ASCEND_20");
        }
    }

    public static void retroactiveAmethystUnlock() {
        if (UnlockTracker.isAchievementUnlocked("AMETHYST_PLUS")) {
            UnlockTracker.unlockAchievement("AMETHYST");
        }
    }

    public static int getVictory(CharStat c) {
        return c.getVictoryCount();
    }

    public static void unlockAscension(CharStat c) {
        c.unlockAscension();
    }

    public static void incrementVictory(CharStat c) {
        c.incrementVictory();
    }

    public static void incrementAscension(CharStat c) {
        c.incrementAscension();
    }

    public static void incrementDeath(CharStat c) {
        c.incrementDeath();
    }

    public static void incrementVictoryIfZero(CharStat c) {
        if (c.getVictoryCount() == 0) {
            c.incrementVictory();
        }
    }

    public static void incrementEnemySlain() {
        AbstractDungeon.player.getCharStat().incrementEnemySlain();
    }

    public static void incrementBossSlain() {
        AbstractDungeon.player.getCharStat().incrementBossSlain();
    }

    public static void incrementPlayTime(long time) {
        AbstractDungeon.player.getCharStat().incrementPlayTime(time);
    }

    public void render(SpriteBatch sb) {
        renderStatScreen(sb);
        this.button.render(sb);
        this.scrollBar.render(sb);
    }

    private void renderStatScreen(SpriteBatch sb) {
        float renderY = this.scrollY;
        renderHeader(sb, NAMES[0], this.screenX, renderY);
        all.render(sb, this.screenX, renderY);
        renderY -= 400.0F * Settings.scale;

        renderHeader(sb, NAMES[1], this.screenX, renderY);
        achievements.render(sb, renderY);
        renderY -= 2200.0F * Settings.scale;

        for (AbstractPlayer c : CardCrawlGame.characterManager.getAllCharacters()) {
            c.renderStatScreen(sb, this.screenX, renderY);
            renderY -= 400.0F * Settings.scale;
        }

        if (Settings.isControllerMode) {
            this.allCharsHb.move(300.0F * Settings.scale, this.scrollY + 600.0F * Settings.scale);
            this.ironcladHb.move(300.0F * Settings.scale, this.scrollY - 1600.0F * Settings.scale);

            if (this.silentHb != null) {
                this.silentHb.move(300.0F * Settings.scale, this.scrollY - 2000.0F * Settings.scale);
            }
            if (this.defectHb != null) {
                this.defectHb.move(300.0F * Settings.scale, this.scrollY - 2400.0F * Settings.scale);
            }
            if (this.watcherHb != null) {
                this.watcherHb.move(300.0F * Settings.scale, this.scrollY - 2800.0F * Settings.scale);
            }
        }
    }

    public static void renderHeader(SpriteBatch sb, String text, float screenX, float renderY) {
        FontHelper.renderSmartText(sb, FontHelper.charTitleFont, text, screenX + 50.0F * Settings.scale,
                renderY + 850.0F * Settings.yScale, 9999.0F, 32.0F * Settings.scale, Settings.CREAM_COLOR);
    }

    public boolean statScreenUnlocked() {
        ArrayList<CharStat> allCharStats = CardCrawlGame.characterManager.getAllCharacterStats();
        for (CharStat cs : allCharStats) {
            if (cs.bossKilled > 0 || cs.getDeathCount() > 0) {
                return true;
            }
        }
        return false;
    }

    public boolean dailiesUnlocked() {
        if (Settings.isDemo) {
            return false;
        }
        return ((AbstractDungeon.player.getCharStat()).furthestAscent > 17);
    }

    public boolean trialsUnlocked() {
        return (AbstractDungeon.player.getCharStat().getVictoryCount() > 0);
    }

    public int getTotalVictories() {
        ArrayList<CharStat> allCharStats = CardCrawlGame.characterManager.getAllCharacterStats();
        int victoryTotal = 0;
        for (CharStat cs : allCharStats) {
            victoryTotal += cs.getVictoryCount();
        }
        return victoryTotal;
    }

    public void scrolledUsingBar(float newPercent) {
        this.scrollY = MathHelper.valueFromPercentBetween(this.scrollLowerBound, this.scrollUpperBound, newPercent);
        this.scrollTargetY = this.scrollY;
        updateBarPosition();
    }

    private void updateBarPosition() {
        float percent = MathHelper.percentFromValueBetween(this.scrollLowerBound, this.scrollUpperBound, this.scrollY);
        this.scrollBar.parentScrolledToPercent(percent);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\stats\
 * StatsScreen.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


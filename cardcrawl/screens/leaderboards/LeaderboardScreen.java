package com.megacrit.cardcrawl.screens.leaderboards;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.controller.CInputHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;
import com.megacrit.cardcrawl.screens.mainMenu.MenuCancelButton;
import com.megacrit.cardcrawl.unlock.UnlockTracker;

import java.util.ArrayList;

public class LeaderboardScreen {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("LeaderboardsScreen");
    public static final String[] TEXT = uiStrings.TEXT;
    public MenuCancelButton button = new MenuCancelButton();
    public boolean screenUp = false;
    public ArrayList<LeaderboardEntry> entries = new ArrayList<>();
    public boolean waiting = true;
    public boolean refresh = true;
    public ArrayList<FilterButton> charButtons = new ArrayList<>();
    public ArrayList<FilterButton> regionButtons = new ArrayList<>();
    public ArrayList<FilterButton> typeButtons = new ArrayList<>();
    public static final float RANK_X = 1000.0F * Settings.scale;
    public static final float NAME_X = 1160.0F * Settings.scale;
    public static final float SCORE_X = 1500.0F * Settings.scale;
    public String charLabel = TEXT[2];
    public String regionLabel = TEXT[3];
    public String typeLabel = TEXT[4];
    public int currentStartIndex;
    public int currentEndIndex;
    private static final float FILTER_SPACING_X = 100.0F * Settings.scale;
    private Hitbox prevHb;
    private Hitbox nextHb;
    private Hitbox viewMyScoreHb = new Hitbox(250.0F * Settings.scale, 80.0F * Settings.scale);

    public boolean viewMyScore = false;
    private float lineFadeInTimer = 0.0F;
    private static final float LINE_THICKNESS = 4.0F * Settings.scale;

    public LeaderboardScreen() {
        this.prevHb = new Hitbox(110.0F * Settings.scale, 110.0F * Settings.scale);
        this.prevHb.move(880.0F * Settings.scale, 530.0F * Settings.scale);
        this.nextHb = new Hitbox(110.0F * Settings.scale, 110.0F * Settings.scale);
        this.nextHb.move(1740.0F * Settings.scale, 530.0F * Settings.scale);
        this.viewMyScoreHb.move(1300.0F * Settings.scale, 80.0F * Settings.scale);
    }

    public void update() {
        updateControllerInput();

        for (FilterButton b : this.charButtons) {
            b.update();
        }

        for (FilterButton b : this.regionButtons) {
            b.update();
        }

        for (FilterButton b : this.typeButtons) {
            b.update();
        }

        this.button.update();

        if (this.button.hb.clicked || InputHelper.pressedEscape) {
            InputHelper.pressedEscape = false;
            hide();
        }

        if (!this.entries.isEmpty() && !this.waiting) {
            this.lineFadeInTimer = MathHelper.slowColorLerpSnap(this.lineFadeInTimer, 1.0F);
        }

        if (this.refresh) {
            this.refresh = false;
            this.waiting = true;
            this.lineFadeInTimer = 0.0F;
            this.currentStartIndex = 1;
            this.currentEndIndex = 20;
            getData(this.currentStartIndex, this.currentEndIndex);
        }

        updateViewMyScore();
        updateArrows();
    }

    private enum LeaderboardSelectionType {
        NONE, CHAR, REGION, TYPE;
    }

    private void updateControllerInput() {
        if (!Settings.isControllerMode) {
            return;
        }

        LeaderboardSelectionType type = LeaderboardSelectionType.NONE;
        boolean anyHovered = false;
        int index = 0;

        for (FilterButton b : this.charButtons) {
            if (b.hb.hovered) {
                anyHovered = true;
                type = LeaderboardSelectionType.CHAR;
                break;
            }
            index++;
        }

        if (!anyHovered) {
            index = 0;
            for (FilterButton b : this.regionButtons) {
                if (b.hb.hovered) {
                    anyHovered = true;
                    type = LeaderboardSelectionType.REGION;
                    break;
                }
                index++;
            }
        }

        if (!anyHovered) {
            index = 0;
            for (FilterButton b : this.typeButtons) {
                if (b.hb.hovered) {
                    anyHovered = true;
                    type = LeaderboardSelectionType.TYPE;
                    break;
                }
                index++;
            }
        }

        if (!anyHovered) {
            Gdx.input.setCursorPosition(
                    (int) ((FilterButton) this.charButtons.get(0)).hb.cX, Settings.HEIGHT -
                            (int) ((FilterButton) this.charButtons.get(0)).hb.cY);
        } else {
            switch (type) {
                case CHAR:
                    if (CInputActionSet.left.isJustPressed() || CInputActionSet.altLeft.isJustPressed()) {
                        index--;
                        if (index < 0) {
                            return;
                        }
                        Gdx.input.setCursorPosition(
                                (int) ((FilterButton) this.charButtons.get(index)).hb.cX, Settings.HEIGHT -
                                        (int) ((FilterButton) this.charButtons.get(index)).hb.cY);
                        break;
                    }
                    if (CInputActionSet.right.isJustPressed() || CInputActionSet.altRight.isJustPressed()) {
                        index++;
                        if (index > this.charButtons.size() - 1) {
                            return;
                        }
                        Gdx.input.setCursorPosition(
                                (int) ((FilterButton) this.charButtons.get(index)).hb.cX, Settings.HEIGHT -
                                        (int) ((FilterButton) this.charButtons.get(index)).hb.cY);
                        break;
                    }
                    if (CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) {
                        if (index > this.regionButtons.size() - 1) {
                            index = this.regionButtons.size() - 1;
                        }
                        Gdx.input.setCursorPosition(
                                (int) ((FilterButton) this.regionButtons.get(index)).hb.cX, Settings.HEIGHT -
                                        (int) ((FilterButton) this.regionButtons.get(index)).hb.cY);
                    }
                    break;
                case REGION:
                    if (CInputActionSet.left.isJustPressed() || CInputActionSet.altLeft.isJustPressed()) {
                        index--;
                        if (index < 0) {
                            return;
                        }
                        CInputHelper.setCursor(((FilterButton) this.regionButtons.get(index)).hb);
                        break;
                    }
                    if (CInputActionSet.right.isJustPressed() || CInputActionSet.altRight.isJustPressed()) {
                        index++;
                        if (index > this.regionButtons.size() - 1) {
                            return;
                        }
                        CInputHelper.setCursor(((FilterButton) this.regionButtons.get(index)).hb);
                        break;
                    }
                    if (CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) {
                        if (index > this.typeButtons.size() - 1) {
                            index = this.typeButtons.size() - 1;
                        }
                        CInputHelper.setCursor(((FilterButton) this.typeButtons.get(index)).hb);
                        break;
                    }
                    if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
                        if (index > this.charButtons.size() - 1) {
                            index = this.charButtons.size() - 1;
                        }
                        CInputHelper.setCursor(((FilterButton) this.charButtons.get(index)).hb);
                    }
                    break;
                case TYPE:
                    if (CInputActionSet.left.isJustPressed() || CInputActionSet.altLeft.isJustPressed()) {
                        index--;
                        if (index < 0) {
                            return;
                        }
                        CInputHelper.setCursor(((FilterButton) this.typeButtons.get(index)).hb);
                        break;
                    }
                    if (CInputActionSet.right.isJustPressed() || CInputActionSet.altRight.isJustPressed()) {
                        index++;
                        if (index > this.typeButtons.size() - 1) {
                            return;
                        }
                        CInputHelper.setCursor(((FilterButton) this.typeButtons.get(index)).hb);
                        break;
                    }
                    if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
                        if (index > this.regionButtons.size() - 1) {
                            index = this.regionButtons.size() - 1;
                        }
                        CInputHelper.setCursor(((FilterButton) this.regionButtons.get(index)).hb);
                    }
                    break;
            }
        }
    }

    private void updateViewMyScore() {
        if (((FilterButton) this.regionButtons.get(0)).active) {
            return;
        }

        this.viewMyScoreHb.update();

        if (this.viewMyScoreHb.justHovered) {
            CardCrawlGame.sound.play("UI_HOVER");
        } else if (this.viewMyScoreHb.hovered && InputHelper.justClickedLeft) {
            this.viewMyScoreHb.clickStarted = true;
            CardCrawlGame.sound.play("UI_CLICK_1");
        } else if (this.viewMyScoreHb.clicked || CInputActionSet.topPanel.isJustPressed()) {
            this.viewMyScoreHb.clicked = false;
            this.viewMyScore = true;
            this.waiting = true;
            getData(this.currentStartIndex, this.currentEndIndex);
        }
    }

    private void updateArrows() {
        if (this.waiting) {
            return;
        }

        if (this.entries.size() == 20) {
            this.nextHb.update();
            if (this.nextHb.justHovered) {
                CardCrawlGame.sound.play("UI_HOVER");
            } else if (this.nextHb.hovered && InputHelper.justClickedLeft) {
                this.nextHb.clickStarted = true;
                CardCrawlGame.sound.play("UI_CLICK_1");
            } else if (this.nextHb.clicked || CInputActionSet.pageRightViewExhaust.isJustPressed()) {
                this.nextHb.clicked = false;
                this.currentStartIndex = this.currentEndIndex + 1;
                this.currentEndIndex = this.currentStartIndex + 19;
                this.waiting = true;
                getData(this.currentStartIndex, this.currentEndIndex);
            }
        }

        if (this.currentStartIndex != 1) {
            this.prevHb.update();
            if (this.prevHb.justHovered) {
                CardCrawlGame.sound.play("UI_HOVER");
            } else if (this.prevHb.hovered && InputHelper.justClickedLeft) {
                this.prevHb.clickStarted = true;
                CardCrawlGame.sound.play("UI_CLICK_1");
            } else if (this.prevHb.clicked || CInputActionSet.pageLeftViewDeck.isJustPressed()) {
                this.prevHb.clicked = false;
                this.currentStartIndex -= 20;
                if (this.currentStartIndex < 1) {
                    this.currentStartIndex = 1;
                }
                this.currentEndIndex = this.currentStartIndex + 19;
                this.waiting = true;
                getData(this.currentStartIndex, this.currentEndIndex);
            }
        }
    }

    private void getData(int startIndex, int endIndex) {
        AbstractPlayer.PlayerClass tmpClass = null;
        FilterButton.RegionSetting rSetting = null;
        FilterButton.LeaderboardType lType = null;

        for (FilterButton b : this.charButtons) {
            if (b.active) {
                tmpClass = b.pClass;

                break;
            }
        }
        for (FilterButton b : this.regionButtons) {
            if (b.active) {
                rSetting = b.rType;

                break;
            }
        }
        for (FilterButton b : this.typeButtons) {
            if (b.active) {
                lType = b.lType;

                break;
            }
        }
        if (tmpClass != null && rSetting != null && lType != null) {
            CardCrawlGame.publisherIntegration.getLeaderboardEntries(tmpClass, rSetting, lType, startIndex, endIndex);
        }
    }

    public void open() {
        CardCrawlGame.mainMenuScreen.screen = MainMenuScreen.CurScreen.LEADERBOARD;
        if (this.charButtons.isEmpty()) {
            this.charButtons.add(new FilterButton("ironclad.png", true, AbstractPlayer.PlayerClass.IRONCLAD));
            this.charButtons.add(new FilterButton("silent.png", false, AbstractPlayer.PlayerClass.THE_SILENT));
            if (!UnlockTracker.isCharacterLocked("Defect")) {
                this.charButtons.add(new FilterButton("defect.png", false, AbstractPlayer.PlayerClass.DEFECT));
            }
            if (!UnlockTracker.isCharacterLocked("Watcher")) {
                this.charButtons.add(new FilterButton("watcher.png", false, AbstractPlayer.PlayerClass.WATCHER));
            }

            this.regionButtons.add(new FilterButton("friends.png", true, FilterButton.RegionSetting.FRIEND));
            this.regionButtons.add(new FilterButton("global.png", false, FilterButton.RegionSetting.GLOBAL));

            this.typeButtons.add(new FilterButton("score.png", true, FilterButton.LeaderboardType.HIGH_SCORE));
            this.typeButtons.add(new FilterButton("chain.png", false, FilterButton.LeaderboardType.CONSECUTIVE_WINS));
            this.typeButtons.add(new FilterButton("time.png", false, FilterButton.LeaderboardType.FASTEST_WIN));
        }

        this.button.show(TEXT[0]);
        this.screenUp = true;
    }

    public void hide() {
        CardCrawlGame.sound.play("DECK_CLOSE", 0.1F);
        this.button.hide();
        this.screenUp = false;
        FontHelper.ClearLeaderboardFontTextures();
        CardCrawlGame.mainMenuScreen.panelScreen.refresh();
    }

    public void render(SpriteBatch sb) {
        renderScoreHeaders(sb);
        renderScores(sb);
        renderViewMyScoreBox(sb);
        renderFilters(sb);
        renderArrows(sb);
        this.button.render(sb);
    }

    private void renderFilters(SpriteBatch sb) {
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.charTitleFont, TEXT[1], 240.0F * Settings.scale,
                920.0F * Settings.scale, Settings.GOLD_COLOR);

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.panelNameFont, this.charLabel, 280.0F * Settings.scale,
                860.0F * Settings.scale, Settings.CREAM_COLOR);

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.panelNameFont, this.regionLabel, 280.0F * Settings.scale,
                680.0F * Settings.scale, Settings.CREAM_COLOR);

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.panelNameFont, this.typeLabel, 280.0F * Settings.scale,
                500.0F * Settings.scale, Settings.CREAM_COLOR);

        int i;

        for (i = 0; i < this.charButtons.size(); i++) {
            ((FilterButton) this.charButtons.get(i)).render(sb, 340.0F * Settings.scale + i * FILTER_SPACING_X,
                    780.0F * Settings.scale);
        }

        for (i = 0; i < this.regionButtons.size(); i++) {
            ((FilterButton) this.regionButtons.get(i)).render(sb, 340.0F * Settings.scale + i * FILTER_SPACING_X,
                    600.0F * Settings.scale);
        }

        for (i = 0; i < this.typeButtons.size(); i++) {
            ((FilterButton) this.typeButtons.get(i)).render(sb, 340.0F * Settings.scale + i * FILTER_SPACING_X,
                    420.0F * Settings.scale);
        }
    }

    private void renderArrows(SpriteBatch sb) {
        boolean renderLeftArrow = true;

        for (FilterButton b : this.regionButtons) {
            if (b.rType == FilterButton.RegionSetting.FRIEND && this.entries.size() < 20) {
                renderLeftArrow = false;
            }
        }

        if (this.currentStartIndex != 1 && renderLeftArrow) {
            sb.setColor(new Color(1.0F, 1.0F, 1.0F, this.lineFadeInTimer));
            sb.draw(ImageMaster.POPUP_ARROW, this.prevHb.cX - 128.0F, this.prevHb.cY - 128.0F, 128.0F, 128.0F, 256.0F,
                    256.0F, Settings.scale * 0.75F, Settings.scale * 0.75F, 0.0F, 0, 0, 256, 256, false, false);

            if (this.prevHb.hovered) {
                sb.setBlendFunction(770, 1);
                sb.setColor(new Color(1.0F, 1.0F, 1.0F, this.lineFadeInTimer / 2.0F));
                sb.draw(ImageMaster.POPUP_ARROW, this.prevHb.cX - 128.0F, this.prevHb.cY - 128.0F, 128.0F, 128.0F,
                        256.0F, 256.0F, Settings.scale * 0.75F, Settings.scale * 0.75F, 0.0F, 0, 0, 256, 256, false,
                        false);

                sb.setBlendFunction(770, 771);
            }

            if (Settings.isControllerMode) {
                sb.draw(CInputActionSet.pageLeftViewDeck
                        .getKeyImg(), this.prevHb.cX - 32.0F + 0.0F * Settings.scale,
                        this.prevHb.cY - 32.0F - 100.0F * Settings.scale, 32.0F, 32.0F, 64.0F, 64.0F, Settings.scale,
                        Settings.scale, 0.0F, 0, 0, 64, 64, false, false);
            }

            this.prevHb.render(sb);
        }

        if (this.entries.size() == 20) {
            sb.setColor(new Color(1.0F, 1.0F, 1.0F, this.lineFadeInTimer));
            sb.draw(ImageMaster.POPUP_ARROW, this.nextHb.cX - 128.0F, this.nextHb.cY - 128.0F, 128.0F, 128.0F, 256.0F,
                    256.0F, Settings.scale * 0.75F, Settings.scale * 0.75F, 0.0F, 0, 0, 256, 256, true, false);

            if (this.nextHb.hovered) {
                sb.setBlendFunction(770, 1);
                sb.setColor(new Color(1.0F, 1.0F, 1.0F, this.lineFadeInTimer / 2.0F));
                sb.draw(ImageMaster.POPUP_ARROW, this.nextHb.cX - 128.0F, this.nextHb.cY - 128.0F, 128.0F, 128.0F,
                        256.0F, 256.0F, Settings.scale * 0.75F, Settings.scale * 0.75F, 0.0F, 0, 0, 256, 256, true,
                        false);

                sb.setBlendFunction(770, 771);
            }

            if (Settings.isControllerMode) {
                sb.draw(CInputActionSet.pageRightViewExhaust
                        .getKeyImg(), this.nextHb.cX - 32.0F + 0.0F * Settings.scale,
                        this.nextHb.cY - 32.0F - 100.0F * Settings.scale, 32.0F, 32.0F, 64.0F, 64.0F, Settings.scale,
                        Settings.scale, 0.0F, 0, 0, 64, 64, false, false);
            }

            this.nextHb.render(sb);
        }
    }

    private void renderScoreHeaders(SpriteBatch sb) {
        Color creamColor = new Color(1.0F, 0.965F, 0.886F, this.lineFadeInTimer);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.charTitleFont, TEXT[10], 960.0F * Settings.scale,
                920.0F * Settings.scale, new Color(0.937F, 0.784F, 0.317F, this.lineFadeInTimer));

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.panelNameFont, TEXT[6], RANK_X, 860.0F * Settings.scale,
                creamColor);

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.panelNameFont, TEXT[7], NAME_X, 860.0F * Settings.scale,
                creamColor);

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.panelNameFont, TEXT[8], SCORE_X, 860.0F * Settings.scale,
                creamColor);

        sb.setColor(creamColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 1138.0F * Settings.scale, 168.0F * Settings.scale, LINE_THICKNESS,
                692.0F * Settings.scale);

        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 1480.0F * Settings.scale, 168.0F * Settings.scale, LINE_THICKNESS,
                692.0F * Settings.scale);

        sb.setColor(new Color(0.0F, 0.0F, 0.0F, this.lineFadeInTimer * 0.75F));
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 982.0F * Settings.scale, 814.0F * Settings.scale, 630.0F * Settings.scale,
                16.0F * Settings.scale);

        sb.setColor(creamColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 982.0F * Settings.scale, 820.0F * Settings.scale, 630.0F * Settings.scale,
                LINE_THICKNESS);
    }

    private void renderViewMyScoreBox(SpriteBatch sb) {
        if (((FilterButton) this.regionButtons.get(0)).active || this.waiting) {
            return;
        }

        if (this.viewMyScoreHb.hovered) {
            FontHelper.renderFontCentered(sb, FontHelper.cardTitleFont, TEXT[5], 1310.0F * Settings.scale,
                    80.0F * Settings.scale, Settings.GREEN_TEXT_COLOR);

        } else {

            FontHelper.renderFontCentered(sb, FontHelper.cardTitleFont, TEXT[5], 1310.0F * Settings.scale,
                    80.0F * Settings.scale, Settings.GOLD_COLOR);
        }

        if (Settings.isControllerMode) {
            sb.draw(CInputActionSet.topPanel
                    .getKeyImg(),
                    1270.0F * Settings.scale - 32.0F -
                            FontHelper.getSmartWidth(FontHelper.cardTitleFont, TEXT[5], 9999.0F, 0.0F) / 2.0F,
                    -32.0F + 80.0F * Settings.scale, 32.0F, 32.0F, 64.0F, 64.0F, Settings.scale, Settings.scale, 0.0F,
                    0, 0, 64, 64, false, false);
        }

        this.viewMyScoreHb.render(sb);
    }

    private void renderScores(SpriteBatch sb) {
        if (!this.waiting) {
            if (this.entries.isEmpty()) {
                FontHelper.renderFontCentered(sb, FontHelper.panelNameFont, TEXT[12], 1300.0F * Settings.scale,
                        540.0F * Settings.scale, Settings.GOLD_COLOR);

            } else {

                for (int i = 0; i < this.entries.size(); i++) {
                    ((LeaderboardEntry) this.entries.get(i)).render(sb, i);
                }
            }

        } else if (CardCrawlGame.publisherIntegration.isInitialized()) {
            FontHelper.renderFontCentered(sb, FontHelper.panelNameFont, TEXT[9], 1300.0F * Settings.scale,
                    540.0F * Settings.scale, Settings.GOLD_COLOR);

        } else {

            FontHelper.renderFontCentered(sb, FontHelper.panelNameFont, TEXT[11], 1300.0F * Settings.scale,
                    540.0F * Settings.scale, Settings.RED_TEXT_COLOR);
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\
 * leaderboards\LeaderboardScreen.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */


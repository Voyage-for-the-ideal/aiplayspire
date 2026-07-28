package com.megacrit.cardcrawl.screens.options;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.controller.CInputHelper;
import com.megacrit.cardcrawl.helpers.input.InputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;
import com.megacrit.cardcrawl.screens.mainMenu.MenuCancelButton;
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBar;
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBarListener;

import java.util.ArrayList;

public class InputSettingsScreen
        implements RemapInputElementListener, HitboxListener, ScrollBarListener {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("InputSettingsScreen");
    public static final String[] TEXT = uiStrings.TEXT;

    private static final String TAB_HEADER = TEXT[0];
    private static final String GAME_SETTINNGS_TAB_HEADER = TEXT[1];
    private static final String RETURN_BUTTON_TEXT = TEXT[2];

    private static final boolean ALLOW_OVERSCROLL = false;
    private static final int BG_RAW_WIDTH = 1920;
    private static final int BG_RAW_HEIGHT = 1080;
    private static final float SHOW_X = 0.0F;
    private static final float HIDE_X = -1100.0F * Settings.scale;

    private static final float GAME_SETTINGS_BUTTON_WIDTH = 360.0F * Settings.scale;
    private static final float ROW_X_POSITION = Settings.WIDTH / 2.0F - 37.0F * Settings.scale;
    private static final float ROW_TABLE_VERTICAL_EXTRA_PADDING = 10.0F * Settings.scale;
    private static final float ROW_PADDING = 0.0F * Settings.scale;
    private static final float ROW_Y_DIFF = RemapInputElement.ROW_HEIGHT + ROW_PADDING;

    private static final float SCROLL_CONTAINER_VISIBLE_HEIGHT = 651.0F * Settings.scale;
    private static final float SCROLL_CONTAINER_BOTTOM = Settings.OPTION_Y - 360.0F * Settings.scale;
    private static final float SCROLL_CONTAINER_TOP = SCROLL_CONTAINER_BOTTOM + SCROLL_CONTAINER_VISIBLE_HEIGHT;

    private static final float SCROLLBAR_X = Settings.WIDTH / 2.0F + 576.0F * Settings.scale;
    private static final float SCROLLBAR_Y = (Settings.HEIGHT / 2) - 61.0F * Settings.scale;
    private static final float SCROLLBAR_HEIGHT = 584.0F * Settings.scale;

    private static final float RESET_BUTTON_CY = (Settings.isSixteenByTen ? 100.0F : 70.0F) * Settings.scale;

    public MenuCancelButton button = new MenuCancelButton();
    private ScrollBar scrollBar = new ScrollBar(this, SCROLLBAR_X, SCROLLBAR_Y, SCROLLBAR_HEIGHT);
    private Hitbox resetButtonHb = new Hitbox(300.0F * Settings.scale, 72.0F * Settings.scale);
    private ArrayList<RemapInputElement> elements = new ArrayList<>();
    private Hitbox gameSettingsHb = new Hitbox(GAME_SETTINGS_BUTTON_WIDTH, Settings.scale * 72.0F);

    private GiantToggleButton controllerEnabledToggleButton;
    private GiantToggleButton touchscreenModeButton = null;

    private float screenX = HIDE_X, targetX = HIDE_X;
    private float maxScrollAmount;
    private float targetScrollOffset = 0.0F;
    private float visibleScrollOffset = 0.0F;

    public boolean screenUp = false;

    private Hitbox elementHb = null;

    public InputSettingsScreen() {
        if (Settings.isConsoleBuild) {
            this.controllerEnabledToggleButton = new GiantToggleButton(GiantToggleButton.ToggleType.CONTROLLER_ENABLED,
                    Settings.WIDTH * 0.6F, RESET_BUTTON_CY, TEXT[48]);

            this.resetButtonHb.move(Settings.WIDTH * 0.35F, RESET_BUTTON_CY);
        } else {
            this.controllerEnabledToggleButton = new GiantToggleButton(GiantToggleButton.ToggleType.CONTROLLER_ENABLED,
                    Settings.WIDTH * 0.42F, RESET_BUTTON_CY, TEXT[48]);

            this.touchscreenModeButton = new GiantToggleButton(GiantToggleButton.ToggleType.TOUCHSCREEN_ENABLED,
                    Settings.WIDTH * 0.65F, RESET_BUTTON_CY, TEXT[49]);

            this.resetButtonHb.move(Settings.WIDTH * 0.25F, RESET_BUTTON_CY);
        }
    }

    public void open() {
        open(true);
    }

    public void open(boolean animated) {
        this.targetX = 0.0F;
        this.targetScrollOffset = 0.0F;
        this.visibleScrollOffset = 0.0F;

        if (animated) {
            this.button.show(RETURN_BUTTON_TEXT);
        } else {
            this.button.showInstantly(RETURN_BUTTON_TEXT);
        }

        this.screenUp = true;
        CardCrawlGame.mainMenuScreen.screen = MainMenuScreen.CurScreen.INPUT_SETTINGS;
        refreshData();
        this.gameSettingsHb.move(Settings.WIDTH / 2.0F - 438.0F * Settings.scale,
                Settings.OPTION_Y + 382.0F * Settings.scale);
        this.scrollBar.isBackgroundVisible = false;
        this.scrollBar.setCenter(SCROLLBAR_X, SCROLLBAR_Y);

        if (CardCrawlGame.isInARun()) {
            AbstractDungeon.screen = AbstractDungeon.CurrentScreen.INPUT_SETTINGS;
        }
    }

    private void refreshData() {
        this.elementHb = null;
        this.elements.clear();
        this.elements.add(new RemapInputElementHeader(TEXT[3], TEXT[4], TEXT[5]));

        if (Settings.isControllerMode) {
            this.elements.add(new RemapInputElement(this, TEXT[28], InputActionSet.confirm, CInputActionSet.select));
            this.elements.add(new RemapInputElement(this, TEXT[29], InputActionSet.cancel, CInputActionSet.cancel));
            this.elements.add(new RemapInputElement(this, TEXT[30], null, CInputActionSet.topPanel));
            this.elements.add(new RemapInputElement(this, TEXT[31], InputActionSet.endTurn, CInputActionSet.proceed));

            this.elements.add(
                    new RemapInputElement(this, TEXT[32], InputActionSet.masterDeck, CInputActionSet.pageLeftViewDeck));

            this.elements.add(new RemapInputElement(this, TEXT[33], InputActionSet.exhaustPile,
                    CInputActionSet.pageRightViewExhaust));

            this.elements.add(new RemapInputElement(this, TEXT[34], InputActionSet.map, CInputActionSet.map));
            this.elements.add(new RemapInputElement(this, TEXT[35], null, CInputActionSet.settings));
            this.elements.add(new RemapInputElement(this, TEXT[36], InputActionSet.drawPile, CInputActionSet.drawPile));
            this.elements.add(
                    new RemapInputElement(this, TEXT[37], InputActionSet.discardPile, CInputActionSet.discardPile));

            this.elements.add(new RemapInputElement(this, TEXT[38], InputActionSet.up, CInputActionSet.up));
            this.elements.add(new RemapInputElement(this, TEXT[39], InputActionSet.down, CInputActionSet.down));
            this.elements.add(new RemapInputElement(this, TEXT[40], InputActionSet.left, CInputActionSet.left));
            this.elements.add(new RemapInputElement(this, TEXT[41], InputActionSet.right, CInputActionSet.right));
            this.elements.add(new RemapInputElement(this, TEXT[42], null, CInputActionSet.altUp));
            this.elements.add(new RemapInputElement(this, TEXT[43], null, CInputActionSet.altDown));
            this.elements.add(new RemapInputElement(this, TEXT[44], null, CInputActionSet.altLeft));
            this.elements.add(new RemapInputElement(this, TEXT[45], null, CInputActionSet.altRight));
        } else {
            this.elements.add(new RemapInputElement(this, TEXT[7], InputActionSet.confirm, CInputActionSet.select));
            this.elements.add(new RemapInputElement(this, TEXT[8], InputActionSet.cancel, CInputActionSet.cancel));

            this.elements.add(new RemapInputElement(this, TEXT[9], InputActionSet.map, CInputActionSet.map));
            this.elements.add(
                    new RemapInputElement(this, TEXT[10], InputActionSet.masterDeck, CInputActionSet.pageLeftViewDeck));

            this.elements.add(new RemapInputElement(this, TEXT[11], InputActionSet.drawPile, CInputActionSet.drawPile));
            this.elements.add(
                    new RemapInputElement(this, TEXT[12], InputActionSet.discardPile, CInputActionSet.discardPile));

            this.elements.add(new RemapInputElement(this, TEXT[13], InputActionSet.exhaustPile,
                    CInputActionSet.pageRightViewExhaust));

            this.elements.add(new RemapInputElement(this, TEXT[14], InputActionSet.endTurn, CInputActionSet.proceed));
            this.elements.add(new RemapInputElement(this, TEXT[50], InputActionSet.peek, CInputActionSet.drawPile));

            this.elements.add(new RemapInputElement(this, TEXT[38], InputActionSet.up, CInputActionSet.up));
            this.elements.add(new RemapInputElement(this, TEXT[39], InputActionSet.down, CInputActionSet.down));
            this.elements.add(new RemapInputElement(this, TEXT[15], InputActionSet.left, CInputActionSet.left));
            this.elements.add(new RemapInputElement(this, TEXT[16], InputActionSet.right, CInputActionSet.right));
            this.elements.add(new RemapInputElement(this, TEXT[17], InputActionSet.selectCard_1));
            this.elements.add(new RemapInputElement(this, TEXT[18], InputActionSet.selectCard_2));
            this.elements.add(new RemapInputElement(this, TEXT[19], InputActionSet.selectCard_3));
            this.elements.add(new RemapInputElement(this, TEXT[20], InputActionSet.selectCard_4));
            this.elements.add(new RemapInputElement(this, TEXT[21], InputActionSet.selectCard_5));
            this.elements.add(new RemapInputElement(this, TEXT[22], InputActionSet.selectCard_6));
            this.elements.add(new RemapInputElement(this, TEXT[23], InputActionSet.selectCard_7));
            this.elements.add(new RemapInputElement(this, TEXT[24], InputActionSet.selectCard_8));
            this.elements.add(new RemapInputElement(this, TEXT[25], InputActionSet.selectCard_9));
            this.elements.add(new RemapInputElement(this, TEXT[26], InputActionSet.selectCard_10));
            this.elements.add(new RemapInputElement(this, TEXT[27], InputActionSet.releaseCard));
        }

        this.maxScrollAmount = ROW_Y_DIFF * this.elements.size() + 2.0F * ROW_TABLE_VERTICAL_EXTRA_PADDING
                - SCROLL_CONTAINER_VISIBLE_HEIGHT;
    }

    public void update() {
        updateControllerInput();
        this.button.update();
        this.controllerEnabledToggleButton.update();
        if (!Settings.isConsoleBuild) {
            this.touchscreenModeButton.update();
        }
        updateScrolling();

        if (this.button.hb.clicked || InputHelper.pressedEscape || CInputActionSet.cancel.isJustPressed()) {
            if (CardCrawlGame.isInARun()) {
                AbstractDungeon.closeCurrentScreen();
            } else {
                InputHelper.pressedEscape = false;
                CardCrawlGame.mainMenuScreen.screen = MainMenuScreen.CurScreen.MAIN_MENU;
            }
            hide();
        }

        updateKeyPositions();
        for (RemapInputElement element : this.elements) {
            element.update();
        }

        if (Settings.isControllerMode &&
                CInputActionSet.pageLeftViewDeck.isJustPressed()) {
            clicked(this.gameSettingsHb);
        }

        this.resetButtonHb.encapsulatedUpdate(this);
        this.gameSettingsHb.encapsulatedUpdate(this);
        this.screenX = MathHelper.uiLerpSnap(this.screenX, this.targetX);
        if (this.elementHb != null && Settings.isControllerMode) {
            CInputHelper.setCursor(this.elementHb);
        }
        this.scrollBar.update();
    }

    private enum HighlightType {
        INPUT, RESET, CONTROLLER_ON_TOGGLE, TOUCHSCREEN_ON_TOGGLE;
    }

    private void updateControllerInput() {
        if (!Settings.isControllerMode) {
            return;
        }

        HighlightType type = HighlightType.INPUT;
        boolean anyHovered = false;
        int index = 0;
        for (RemapInputElement e : this.elements) {
            e.hb.update();
            if (e.hb.hovered) {
                anyHovered = true;
                break;
            }
            index++;
        }

        if (this.resetButtonHb.hovered) {
            type = HighlightType.RESET;
            anyHovered = true;
        } else if (this.controllerEnabledToggleButton.hb.hovered) {
            type = HighlightType.CONTROLLER_ON_TOGGLE;
            anyHovered = true;
        } else if (this.touchscreenModeButton.hb.hovered) {
            type = HighlightType.TOUCHSCREEN_ON_TOGGLE;
            anyHovered = true;
        }

        if (!anyHovered) {
            index = 1;
            CInputHelper.setCursor(((RemapInputElement) this.elements.get(1)).hb);
        } else {
            switch (type) {
                case INPUT:
                    if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
                        index--;
                        if (index < 1) {
                            index = 1;
                        }
                        CInputHelper.setCursor(((RemapInputElement) this.elements.get(index)).hb);
                        this.elementHb = ((RemapInputElement) this.elements.get(index)).hb;
                        break;
                    }
                    if (CInputActionSet.down.isJustPressed() || CInputActionSet.altDown.isJustPressed()) {
                        index++;
                        if (index > this.elements.size() - 1) {
                            CInputHelper.setCursor(this.resetButtonHb);
                            this.elementHb = this.resetButtonHb;
                            break;
                        }
                        CInputHelper.setCursor(((RemapInputElement) this.elements.get(index)).hb);
                        this.elementHb = ((RemapInputElement) this.elements.get(index)).hb;
                    }
                    break;

                case RESET:
                    if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
                        CInputHelper.setCursor(((RemapInputElement) this.elements.get(this.elements.size() - 1)).hb);
                        this.elementHb = ((RemapInputElement) this.elements.get(this.elements.size() - 1)).hb;
                        break;
                    }
                    if (CInputActionSet.right.isJustPressed() || CInputActionSet.altRight.isJustPressed()) {
                        CInputHelper.setCursor(this.controllerEnabledToggleButton.hb);
                        this.elementHb = this.controllerEnabledToggleButton.hb;
                        break;
                    }
                    if (CInputActionSet.right.isJustPressed() || CInputActionSet.altRight.isJustPressed()) {
                        CInputHelper.setCursor(this.touchscreenModeButton.hb);
                        this.elementHb = this.touchscreenModeButton.hb;
                    }
                    break;
                case CONTROLLER_ON_TOGGLE:
                    if (CInputActionSet.up.isJustPressed() || CInputActionSet.altUp.isJustPressed()) {
                        CInputHelper.setCursor(((RemapInputElement) this.elements.get(this.elements.size() - 1)).hb);
                        this.elementHb = ((RemapInputElement) this.elements.get(this.elements.size() - 1)).hb;
                        break;
                    }
                    if (CInputActionSet.left.isJustPressed() || CInputActionSet.altLeft.isJustPressed()) {
                        CInputHelper.setCursor(this.resetButtonHb);
                        this.elementHb = this.resetButtonHb;
                    }
                    break;
            }

        }
        updateControllerScrolling();
    }

    private void updateControllerScrolling() {
        if (Gdx.input.getY() > Settings.HEIGHT * 0.65F) {
            this.targetScrollOffset += Settings.SCROLL_SPEED / 3.0F;
            if (this.targetScrollOffset > this.maxScrollAmount) {
                this.targetScrollOffset = this.maxScrollAmount;
            }
        } else if (Gdx.input.getY() < Settings.HEIGHT * 0.35F) {
            this.targetScrollOffset -= Settings.SCROLL_SPEED / 3.0F;
            if (this.targetScrollOffset < 0.0F) {
                this.targetScrollOffset = 0.0F;
            }
        }
    }

    private void updateScrolling() {
        float targetScrollOffset = this.targetScrollOffset;
        if (InputHelper.scrolledDown) {
            targetScrollOffset += Settings.SCROLL_SPEED * 3.0F;
        } else if (InputHelper.scrolledUp) {
            targetScrollOffset -= Settings.SCROLL_SPEED * 3.0F;
        }

        if (targetScrollOffset != this.targetScrollOffset) {
            this.targetScrollOffset = MathHelper.scrollSnapLerpSpeed(this.targetScrollOffset, targetScrollOffset);

            this.targetScrollOffset = MathUtils.clamp(this.targetScrollOffset, 0.0F, this.maxScrollAmount);
        }

        updateBarPosition();
    }

    public void hide() {
        CardCrawlGame.sound.play("DECK_CLOSE", 0.1F);
        this.targetX = HIDE_X;
        this.button.hide();
        this.screenUp = false;
        InputActionSet.save();
        CInputActionSet.save();
        CardCrawlGame.mainMenuScreen.panelScreen.refresh();
    }

    public void render(SpriteBatch sb) {
        sb.setColor(Color.WHITE);
        renderFullscreenBackground(sb, ImageMaster.INPUT_SETTINGS_BG);

        for (RemapInputElement element : this.elements) {
            element.render(sb);
        }

        renderResetDefaultButton(sb);
        renderFullscreenBackground(sb, ImageMaster.INPUT_SETTINGS_EDGES);

        this.scrollBar.render(sb);
        renderHeader(sb);

        if (Settings.isControllerMode) {
            sb.draw(CInputActionSet.pageLeftViewDeck
                    .getKeyImg(),
                    this.gameSettingsHb.cX - 32.0F -
                            FontHelper.getSmartWidth(FontHelper.panelNameFont, GAME_SETTINNGS_TAB_HEADER, 99999.0F,
                                    2.0F) / 2.0F
                            - 42.0F * Settings.scale,
                    Settings.OPTION_Y - 32.0F + 379.0F * Settings.scale, 32.0F, 32.0F, 64.0F, 64.0F, Settings.scale,
                    Settings.scale, 0.0F, 0, 0, 64, 64, false, false);
        }

        this.controllerEnabledToggleButton.render(sb);
        if (!Settings.isConsoleBuild) {
            this.touchscreenModeButton.render(sb);
        }
        this.button.render(sb);
    }

    private void updateKeyPositions() {
        float x = ROW_X_POSITION;
        float y = SCROLL_CONTAINER_TOP - ROW_TABLE_VERTICAL_EXTRA_PADDING - ROW_Y_DIFF / 2.0F;
        if (Settings.isSixteenByTen) {
            y -= Settings.scale * 4.0F;
        }

        this.visibleScrollOffset = MathHelper.scrollSnapLerpSpeed(this.visibleScrollOffset, this.targetScrollOffset);
        y += this.visibleScrollOffset;

        float maxVisibleY = SCROLL_CONTAINER_TOP - Settings.scale * 10.0F;
        float minVisibleY = SCROLL_CONTAINER_BOTTOM - RemapInputElement.ROW_HEIGHT - Settings.scale * 10.0F;

        for (RemapInputElement element : this.elements) {
            element.move(x, y);
            boolean isInView = (minVisibleY < element.hb.y && element.hb.y < maxVisibleY);
            element.isHidden = !isInView;
            y -= ROW_Y_DIFF;
        }
    }

    private void renderHeader(SpriteBatch sb) {
        Color textColor = this.gameSettingsHb.hovered ? Settings.GOLD_COLOR : Color.LIGHT_GRAY;
        FontHelper.renderFontCentered(sb, FontHelper.panelNameFont, GAME_SETTINNGS_TAB_HEADER, this.gameSettingsHb.cX,
                this.gameSettingsHb.cY, textColor);

        this.gameSettingsHb.render(sb);

        FontHelper.renderFontCentered(sb, FontHelper.panelNameFont, TAB_HEADER,
                this.gameSettingsHb.cX + 396.0F * Settings.scale, this.gameSettingsHb.cY, Settings.GOLD_COLOR);
    }

    private void renderFullscreenBackground(SpriteBatch sb, Texture image) {
        sb.draw(image, Settings.WIDTH / 2.0F - 960.0F, Settings.OPTION_Y - 540.0F, 960.0F, 540.0F, 1920.0F, 1080.0F,
                Settings.scale, Settings.scale, 0.0F, 0, 0, 1920, 1080, false, false);
    }

    private void renderResetDefaultButton(SpriteBatch sb) {
        Color color = this.resetButtonHb.hovered ? Settings.GREEN_TEXT_COLOR : Settings.CREAM_COLOR;
        FontHelper.renderFontCentered(sb, FontHelper.panelEndTurnFont, TEXT[6], this.resetButtonHb.cX,
                this.resetButtonHb.cY, color);

        this.resetButtonHb.render(sb);
    }

    public void didStartRemapping(RemapInputElement element) {
        for (RemapInputElement e : this.elements) {
            e.hasInputFocus = (e == element);
        }
    }

    public boolean willRemap(RemapInputElement element, int oldKey, int newKey) {
        if (oldKey == newKey) {
            return true;
        }
        for (RemapInputElement e : this.elements) {

            if (e.action != null && e.action.getKey() == newKey) {
                e.action.remap(oldKey);
            }
        }
        return true;
    }

    public boolean willRemapController(RemapInputElement element, int oldCode, int newCode) {
        if (oldCode == newCode) {
            return true;
        }
        for (RemapInputElement e : this.elements) {
            if (e.cAction != null && e.cAction.getKey() == newCode) {
                e.cAction.remap(oldCode);
            }
        }
        return true;
    }

    public void hoverStarted(Hitbox hitbox) {
        CardCrawlGame.sound.play("UI_HOVER");
    }

    public void startClicking(Hitbox hitbox) {
        CardCrawlGame.sound.play("UI_CLICK_1");
    }

    public void clicked(Hitbox hb) {
        if (hb == this.resetButtonHb) {
            CardCrawlGame.sound.play("END_TURN");
            InputActionSet.resetToDefaults();
            CInputActionSet.resetToDefaults();
            refreshData();
            updateKeyPositions();
        } else if (hb == this.gameSettingsHb) {
            if (CardCrawlGame.isInARun()) {
                AbstractDungeon.settingsScreen.open(false);
            } else {

                CardCrawlGame.sound.play("END_TURN");
                CardCrawlGame.mainMenuScreen.isSettingsUp = true;
                InputHelper.pressedEscape = false;
                CardCrawlGame.mainMenuScreen.statsScreen.hide();
                CardCrawlGame.mainMenuScreen.cancelButton.hide();
                CardCrawlGame.cancelButton.showInstantly(MainMenuScreen.TEXT[2]);
                CardCrawlGame.mainMenuScreen.screen = MainMenuScreen.CurScreen.SETTINGS;
            }
        }
    }

    public void scrolledUsingBar(float newPercent) {
        this.targetScrollOffset = MathHelper.valueFromPercentBetween(0.0F, this.maxScrollAmount, newPercent);
        this.scrollBar.parentScrolledToPercent(newPercent);
    }

    private void updateBarPosition() {
        float percent = MathHelper.percentFromValueBetween(0.0F, this.maxScrollAmount, this.targetScrollOffset);
        this.scrollBar.parentScrolledToPercent(percent);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\options\
 * InputSettingsScreen.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


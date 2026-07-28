package com.megacrit.cardcrawl.ui.panels;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Clipboard;
import com.codedisaster.steamworks.SteamUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.helpers.input.ScrollInputProcessor;
import com.megacrit.cardcrawl.helpers.steamInput.SteamInputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;

public class SeedPanel {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("SeedPanel");
    public static final String[] TEXT = uiStrings.TEXT;

    public String title;
    public static String textField = "";
    public Hitbox yesHb = new Hitbox(160.0F * Settings.scale, 70.0F * Settings.scale);
    public Hitbox noHb = new Hitbox(160.0F * Settings.scale, 70.0F * Settings.scale);
    private static final int CONFIRM_W = 360;
    private static final int CONFIRM_H = 414;
    private static final int YES_W = 173;
    private static final int NO_W = 161;
    private static final int BUTTON_H = 74;
    private Color screenColor = new Color(0.0F, 0.0F, 0.0F, 0.0F);
    private Color uiColor = new Color(1.0F, 1.0F, 1.0F, 0.0F);
    private float animTimer = 0.0F;
    private float waitTimer = 0.0F;
    private static final float ANIM_TIME = 0.25F;
    public boolean shown = false;
    private static final float SCREEN_DARKNESS = 0.8F;
    public MainMenuScreen.CurScreen sourceScreen = null;

    public void update() {
        if (Gdx.input.isKeyPressed(67) && !textField.equals("") && this.waitTimer <= 0.0F) {
            textField = textField.substring(0, textField.length() - 1);
            this.waitTimer = 0.09F;
        }
        if (this.waitTimer > 0.0F) {
            this.waitTimer -= Gdx.graphics.getDeltaTime();
        }
        if (this.shown) {
            if (this.animTimer != 0.0F) {
                this.animTimer -= Gdx.graphics.getDeltaTime();
                if (this.animTimer < 0.0F) {
                    this.animTimer = 0.0F;
                }
                this.screenColor.a = Interpolation.fade.apply(0.8F, 0.0F, this.animTimer * 1.0F / 0.25F);
                this.uiColor.a = Interpolation.fade.apply(1.0F, 0.0F, this.animTimer * 1.0F / 0.25F);
            } else {
                updateYes();
                updateNo();

                if (InputActionSet.confirm.isJustPressed()) {
                    confirm();
                } else if (InputHelper.pressedEscape) {
                    InputHelper.pressedEscape = false;
                    cancel();
                } else if (InputHelper.isPasteJustPressed()) {
                    Clipboard clipBoard = Gdx.app.getClipboard();
                    String pasteText = clipBoard.getContents();
                    String seedText = SeedHelper.sterilizeString(pasteText);
                    if (!seedText.isEmpty()) {
                        textField = seedText;
                    }
                }

            }
        } else if (this.animTimer != 0.0F) {
            this.animTimer -= Gdx.graphics.getDeltaTime();
            if (this.animTimer < 0.0F) {
                this.animTimer = 0.0F;
            }
            this.screenColor.a = Interpolation.fade.apply(0.0F, 0.8F, this.animTimer * 1.0F / 0.25F);
            this.uiColor.a = Interpolation.fade.apply(0.0F, 1.0F, this.animTimer * 1.0F / 0.25F);
        }
    }

    private void updateYes() {
        this.yesHb.update();
        if (this.yesHb.justHovered) {
            CardCrawlGame.sound.play("UI_HOVER");
        }

        if (InputHelper.justClickedLeft && this.yesHb.hovered) {
            CardCrawlGame.sound.play("UI_CLICK_1");
            this.yesHb.clickStarted = true;
        }

        if (CInputActionSet.proceed.isJustPressed()) {
            CInputActionSet.proceed.unpress();
            this.yesHb.clicked = true;
        }

        if (this.yesHb.clicked) {
            this.yesHb.clicked = false;
            confirm();
        }
    }

    private void updateNo() {
        this.noHb.update();
        if (this.noHb.justHovered) {
            CardCrawlGame.sound.play("UI_HOVER");
        }

        if (InputHelper.justClickedLeft && this.noHb.hovered) {
            CardCrawlGame.sound.play("UI_CLICK_1");
            this.noHb.clickStarted = true;
        }

        if (CInputActionSet.cancel.isJustPressed()) {
            this.noHb.clicked = true;
        }

        if (this.noHb.clicked) {
            this.noHb.clicked = false;
            cancel();
        }
    }

    public void show() {
        Gdx.input.setInputProcessor((InputProcessor) new TypeHelper(true));

        if (SteamInputHelper.numControllers == 1 && CardCrawlGame.clientUtils != null
                && CardCrawlGame.clientUtils.isSteamRunningOnSteamDeck()) {
            CardCrawlGame.clientUtils.showFloatingGamepadTextInput(
                    SteamUtils.FloatingGamepadTextInputMode.ModeSingleLine, 0, 0, Settings.WIDTH,
                    (int) (Settings.HEIGHT * 0.25F));
        }

        this.yesHb.move(860.0F * Settings.scale, Settings.OPTION_Y - 118.0F * Settings.scale);
        this.noHb.move(1062.0F * Settings.scale, Settings.OPTION_Y - 118.0F * Settings.scale);
        this.shown = true;
        this.animTimer = 0.25F;
        textField = SeedHelper.getUserFacingSeedString();
    }

    public void show(MainMenuScreen.CurScreen sourceScreen) {
        show();
        this.sourceScreen = sourceScreen;
    }

    public void confirm() {
        textField = textField.trim();
        try {
            SeedHelper.setSeed(textField);
        } catch (NumberFormatException e) {

            Settings.seed = Long.valueOf(Long.MAX_VALUE);
        }
        close();
    }

    public void cancel() {
        close();
    }

    public void close() {
        this.yesHb.move(-1000.0F, -1000.0F);
        this.noHb.move(-1000.0F, -1000.0F);
        this.shown = false;
        this.animTimer = 0.25F;
        Gdx.input.setInputProcessor((InputProcessor) new ScrollInputProcessor());

        if (this.sourceScreen == null) {
            CardCrawlGame.mainMenuScreen.screen = MainMenuScreen.CurScreen.CHAR_SELECT;
        } else {
            CardCrawlGame.mainMenuScreen.screen = this.sourceScreen;
            this.sourceScreen = null;
        }
    }

    public static boolean isFull() {
        return (textField.length() >= SeedHelper.SEED_DEFAULT_LENGTH);
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.screenColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0.0F, 0.0F, Settings.WIDTH, Settings.HEIGHT);

        sb.setColor(this.uiColor);
        sb.draw(ImageMaster.OPTION_CONFIRM, Settings.WIDTH / 2.0F - 180.0F, Settings.OPTION_Y - 207.0F, 180.0F, 207.0F,
                360.0F, 414.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 360, 414, false, false);

        sb.draw(ImageMaster.RENAME_BOX, Settings.WIDTH / 2.0F - 160.0F, Settings.OPTION_Y - 160.0F, 160.0F, 160.0F,
                320.0F, 320.0F, Settings.scale * 1.1F, Settings.scale * 1.1F, 0.0F, 0, 0, 320, 320, false, false);

        FontHelper.renderSmartText(sb, FontHelper.cardTitleFont, textField,
                Settings.WIDTH / 2.0F - 120.0F * Settings.scale, Settings.OPTION_Y + 4.0F * Settings.scale, 100000.0F,
                0.0F, this.uiColor, 0.82F);

        if (!isFull()) {
            float tmpAlpha = (MathUtils.cosDeg((float) (System.currentTimeMillis() / 3L % 360L)) + 1.25F) / 3.0F
                    * this.uiColor.a;
            FontHelper
                    .renderSmartText(
                            sb, FontHelper.cardTitleFont, "_", Settings.WIDTH / 2.0F - 122.0F * Settings.scale +

                                    FontHelper.getSmartWidth(FontHelper.cardTitleFont, textField, 1000000.0F, 0.0F,
                                            0.82F),
                            Settings.OPTION_Y + 4.0F * Settings.scale, 100000.0F, 0.0F,
                            new Color(1.0F, 1.0F, 1.0F, tmpAlpha), 0.82F);
        }

        Color c = Settings.GOLD_COLOR.cpy();
        c.a = this.uiColor.a;
        FontHelper.renderFontCentered(sb, FontHelper.cardTitleFont, TEXT[1], Settings.WIDTH / 2.0F,
                Settings.OPTION_Y + 126.0F * Settings.scale, c);

        if (this.yesHb.clickStarted) {
            sb.setColor(new Color(1.0F, 1.0F, 1.0F, this.uiColor.a * 0.9F));
            sb.draw(ImageMaster.OPTION_YES, Settings.WIDTH / 2.0F - 86.5F - 100.0F * Settings.scale,
                    Settings.OPTION_Y - 37.0F - 120.0F * Settings.scale, 86.5F, 37.0F, 173.0F, 74.0F, Settings.scale,
                    Settings.scale, 0.0F, 0, 0, 173, 74, false, false);

            sb.setColor(new Color(this.uiColor));
        } else {
            sb.draw(ImageMaster.OPTION_YES, Settings.WIDTH / 2.0F - 86.5F - 100.0F * Settings.scale,
                    Settings.OPTION_Y - 37.0F - 120.0F * Settings.scale, 86.5F, 37.0F, 173.0F, 74.0F, Settings.scale,
                    Settings.scale, 0.0F, 0, 0, 173, 74, false, false);
        }

        if (!this.yesHb.clickStarted && this.yesHb.hovered) {
            sb.setColor(new Color(1.0F, 1.0F, 1.0F, this.uiColor.a * 0.25F));
            sb.setBlendFunction(770, 1);
            sb.draw(ImageMaster.OPTION_YES, Settings.WIDTH / 2.0F - 86.5F - 100.0F * Settings.scale,
                    Settings.OPTION_Y - 37.0F - 120.0F * Settings.scale, 86.5F, 37.0F, 173.0F, 74.0F, Settings.scale,
                    Settings.scale, 0.0F, 0, 0, 173, 74, false, false);

            sb.setBlendFunction(770, 771);
            sb.setColor(this.uiColor);
        }

        if (this.yesHb.clickStarted) {
            c = Color.LIGHT_GRAY.cpy();
        } else if (this.yesHb.hovered) {
            c = Settings.CREAM_COLOR.cpy();
        } else {
            c = Settings.GOLD_COLOR.cpy();
        }
        c.a = this.uiColor.a;
        FontHelper.renderFontCentered(sb, FontHelper.cardTitleFont, TEXT[2],
                Settings.WIDTH / 2.0F - 110.0F * Settings.scale, Settings.OPTION_Y - 118.0F * Settings.scale, c, 0.82F);

        sb.draw(ImageMaster.OPTION_NO, Settings.WIDTH / 2.0F - 80.5F + 106.0F * Settings.scale,
                Settings.OPTION_Y - 37.0F - 120.0F * Settings.scale, 80.5F, 37.0F, 161.0F, 74.0F, Settings.scale,
                Settings.scale, 0.0F, 0, 0, 161, 74, false, false);

        if (!this.noHb.clickStarted && this.noHb.hovered) {
            sb.setColor(new Color(1.0F, 1.0F, 1.0F, this.uiColor.a * 0.25F));
            sb.setBlendFunction(770, 1);
            sb.draw(ImageMaster.OPTION_NO, Settings.WIDTH / 2.0F - 80.5F + 106.0F * Settings.scale,
                    Settings.OPTION_Y - 37.0F - 120.0F * Settings.scale, 80.5F, 37.0F, 161.0F, 74.0F, Settings.scale,
                    Settings.scale, 0.0F, 0, 0, 161, 74, false, false);

            sb.setBlendFunction(770, 771);
            sb.setColor(this.uiColor);
        }

        if (this.noHb.clickStarted) {
            c = Color.LIGHT_GRAY.cpy();
        } else if (this.noHb.hovered) {
            c = Settings.CREAM_COLOR.cpy();
        } else {
            c = Settings.GOLD_COLOR.cpy();
        }
        c.a = this.uiColor.a;
        FontHelper.renderFontCentered(sb, FontHelper.cardTitleFont, TEXT[3],
                Settings.WIDTH / 2.0F + 110.0F * Settings.scale, Settings.OPTION_Y - 118.0F * Settings.scale, c, 0.82F);

        if (this.shown) {
            if (Settings.isControllerMode) {
                sb.draw(CInputActionSet.proceed
                        .getKeyImg(), 770.0F * Settings.scale - 32.0F,
                        Settings.OPTION_Y - 32.0F - 140.0F * Settings.scale, 32.0F, 32.0F, 64.0F, 64.0F, Settings.scale,
                        Settings.scale, 0.0F, 0, 0, 64, 64, false, false);

                sb.draw(CInputActionSet.cancel
                        .getKeyImg(), 1150.0F * Settings.scale - 32.0F,
                        Settings.OPTION_Y - 32.0F - 140.0F * Settings.scale, 32.0F, 32.0F, 64.0F, 64.0F, Settings.scale,
                        Settings.scale, 0.0F, 0, 0, 64, 64, false, false);
            }

            this.yesHb.render(sb);
            this.noHb.render(sb);

            if (!Settings.usesTrophies) {
                FontHelper.renderFontCentered(sb, FontHelper.panelNameFont, TEXT[4], Settings.WIDTH / 2.0F,
                        100.0F * Settings.scale, new Color(1.0F, 0.3F, 0.3F, c.a));

            } else {

                FontHelper.renderFontCentered(sb, FontHelper.panelNameFont, TEXT[5], Settings.WIDTH / 2.0F,
                        100.0F * Settings.scale, new Color(1.0F, 0.3F, 0.3F, c.a));
            }
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcraw\\ui\panels\
 * SeedPanel.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

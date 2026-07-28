package com.megacrit.cardcrawl.ui.buttons;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;

public class CardSelectConfirmButton {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("Confirm Button");
    public static final String[] TEXT = uiStrings.TEXT;

    private static final int W = 512;
    private static final int H = 256;
    private static final float TAKE_Y = 475.0F * Settings.scale;
    private static final float SHOW_X = Settings.WIDTH - 256.0F * Settings.scale;
    private static final float HIDE_X = SHOW_X + 400.0F * Settings.scale;
    private float current_x = HIDE_X;
    private float target_x = this.current_x;

    private boolean isHidden = true;
    public boolean isDisabled = true;
    private Color textColor = Color.WHITE.cpy();
    private Color btnColor = Color.WHITE.cpy();
    private float target_a = 0.0F;

    private String buttonText = "NOT_SET";

    private static final float HITBOX_W = 260.0F * Settings.scale;
    private static final float HITBOX_H = 80.0F * Settings.scale;
    public Hitbox hb = new Hitbox(0.0F, 0.0F, HITBOX_W, HITBOX_H);

    public CardSelectConfirmButton() {
        this.buttonText = TEXT[0];
        this.hb.move(Settings.WIDTH / 2.0F, TAKE_Y);
    }

    public void update() {
        if (!this.isHidden) {
            this.hb.update();
        }

        if (!this.isDisabled) {
            if (this.hb.justHovered) {
                CardCrawlGame.sound.play("UI_HOVER");
            }
            if (this.hb.hovered && InputHelper.justClickedLeft) {
                this.hb.clickStarted = true;
                CardCrawlGame.sound.play("UI_CLICK_1");
            }
        }

        if (this.current_x != this.target_x) {
            this.current_x = MathUtils.lerp(this.current_x, this.target_x, Gdx.graphics.getDeltaTime() * 9.0F);
            if (Math.abs(this.current_x - this.target_x) < Settings.UI_SNAP_THRESHOLD) {
                this.current_x = this.target_x;
            }
        }

        this.textColor.a = MathHelper.fadeLerpSnap(this.textColor.a, this.target_a);
        this.btnColor.a = this.textColor.a;
    }

    public void hideInstantly() {
        this.current_x = HIDE_X;
        this.target_x = HIDE_X;
        this.isHidden = true;
        this.target_a = 0.0F;
        this.textColor.a = 0.0F;
    }

    public void hide() {
        if (!this.isHidden) {
            this.target_a = 0.0F;
            this.target_x = HIDE_X;
            this.isHidden = true;
        }
    }

    public void show() {
        if (this.isHidden) {
            this.textColor.a = 0.0F;
            this.target_a = 1.0F;
            this.target_x = SHOW_X;
            this.isHidden = false;
        }
    }

    public void disable() {
        if (!this.isDisabled) {
            this.hb.hovered = false;
            this.isDisabled = true;
            this.btnColor = Color.GRAY.cpy();
            this.textColor = Color.LIGHT_GRAY.cpy();
        }
    }

    public void enable() {
        if (this.isDisabled) {
            this.isDisabled = false;
            this.btnColor = Color.WHITE.cpy();
            this.textColor = Settings.CREAM_COLOR.cpy();
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.btnColor);

        renderButton(sb);

        if (this.hb.hovered && !this.isDisabled && !this.hb.clickStarted) {
            sb.setBlendFunction(770, 1);
            sb.setColor(new Color(1.0F, 1.0F, 1.0F, 0.3F));
            renderButton(sb);
            sb.setBlendFunction(770, 771);
        }

        if (!this.isHidden) {
            FontHelper.renderFontCentered(sb, FontHelper.buttonLabelFont, this.buttonText, Settings.WIDTH / 2.0F,
                    TAKE_Y, this.textColor);
        }
    }

    private void renderButton(SpriteBatch sb) {
        if (!this.isHidden) {
            if (this.isDisabled) {
                sb.draw(ImageMaster.REWARD_SCREEN_TAKE_USED_BUTTON, Settings.WIDTH / 2.0F - 256.0F, TAKE_Y - 128.0F,
                        256.0F, 128.0F, 512.0F, 256.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 512, 256, false,
                        false);

            } else {

                if (this.hb.clickStarted) {
                    sb.setColor(Color.LIGHT_GRAY);
                }
                sb.draw(ImageMaster.REWARD_SCREEN_TAKE_BUTTON, Settings.WIDTH / 2.0F - 256.0F, TAKE_Y - 128.0F, 256.0F,
                        128.0F, 512.0F, 256.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 512, 256, false, false);
            }

            if (Settings.isControllerMode) {
                sb.draw(CInputActionSet.proceed
                        .getKeyImg(), this.hb.cX - 32.0F - 100.0F * Settings.scale,
                        this.hb.cY - 32.0F + 2.0F * Settings.scale, 32.0F, 32.0F, 64.0F, 64.0F, Settings.scale,
                        Settings.scale, 0.0F, 0, 0, 64, 64, false, false);
            }

            this.hb.render(sb);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcraw\\ui\buttons\
 * CardSelectConfirmButton.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */


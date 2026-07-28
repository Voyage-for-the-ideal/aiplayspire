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
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;

public class ConfirmButton {
    private static final int W = 512;
    private static final int H = 256;
    private static final Color HOVER_BLEND_COLOR = new Color(1.0F, 1.0F, 1.0F, 0.3F);
    private static final Color TEXT_DISABLED_COLOR = new Color(0.6F, 0.6F, 0.6F, 1.0F);
    private static final float SHOW_X = Settings.WIDTH - 256.0F * Settings.scale, DRAW_Y = 128.0F * Settings.scale;
    private static final float HIDE_X = SHOW_X + 400.0F * Settings.scale;
    private float current_x = HIDE_X;
    private float target_x = this.current_x;
    private float controller_offset_x = 0.0F;

    private boolean isHidden = true;
    private float glowAlpha = 0.0F;
    public boolean isDisabled = true;
    public boolean isHovered = false;
    private Color glowColor = Color.WHITE.cpy();

    private String buttonText = "NOT_SET";
    private static final float TEXT_OFFSET_X = 136.0F * Settings.scale;
    private static final float TEXT_OFFSET_Y = 57.0F * Settings.scale;

    public Hitbox hb = new Hitbox(0.0F, 0.0F, 300.0F * Settings.scale, 100.0F * Settings.scale);

    public ConfirmButton() {
        updateText(CardSelectConfirmButton.TEXT[0]);
        this.hb.move(SHOW_X + 106.0F * Settings.scale, DRAW_Y + 60.0F * Settings.scale);
    }

    public ConfirmButton(String label) {
        updateText(label);
        this.hb.move(SHOW_X + 106.0F * Settings.scale, DRAW_Y + 60.0F * Settings.scale);
    }

    public void updateText(String label) {
        this.buttonText = label;
        this.controller_offset_x = FontHelper.getSmartWidth(FontHelper.buttonLabelFont, label, 99999.0F, 0.0F) / 2.0F;
    }

    public void update() {
        if (!this.isHidden) {
            updateGlow();
            this.hb.update();

            if (InputHelper.justClickedLeft && this.hb.hovered && !this.isDisabled) {
                this.hb.clickStarted = true;
                CardCrawlGame.sound.play("UI_CLICK_1");
            }
            if (this.hb.justHovered && !this.isDisabled) {
                CardCrawlGame.sound.play("UI_HOVER");
            }
            this.isHovered = this.hb.hovered;

            if (CInputActionSet.select.isJustPressed()) {
                CInputActionSet.select.unpress();
                this.hb.clicked = true;
            }
        }

        if (this.current_x != this.target_x) {
            this.current_x = MathUtils.lerp(this.current_x, this.target_x, Gdx.graphics.getDeltaTime() * 9.0F);
            if (Math.abs(this.current_x - this.target_x) < Settings.UI_SNAP_THRESHOLD) {
                this.current_x = this.target_x;
            }
        }
    }

    private void updateGlow() {
        this.glowAlpha += Gdx.graphics.getDeltaTime() * 3.0F;
        if (this.glowAlpha < 0.0F) {
            this.glowAlpha *= -1.0F;
        }
        float tmp = MathUtils.cos(this.glowAlpha);
        if (tmp < 0.0F) {
            this.glowColor.a = -tmp / 2.0F + 0.3F;
        } else {
            this.glowColor.a = tmp / 2.0F + 0.3F;
        }
    }

    public void hideInstantly() {
        this.current_x = HIDE_X;
        this.target_x = HIDE_X;
        this.isHidden = true;
    }

    public void hide() {
        if (!this.isHidden) {
            this.target_x = HIDE_X;
            this.isHidden = true;
        }
    }

    public void show() {
        if (this.isHidden) {
            this.glowAlpha = 0.0F;
            this.target_x = SHOW_X;
            this.isHidden = false;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(Color.WHITE);
        renderShadow(sb);
        sb.setColor(this.glowColor);
        renderOutline(sb);
        sb.setColor(Color.WHITE);
        renderButton(sb);

        if (this.hb.hovered && !this.isDisabled && !this.hb.clickStarted) {
            sb.setBlendFunction(770, 1);
            sb.setColor(HOVER_BLEND_COLOR);
            renderButton(sb);
            sb.setBlendFunction(770, 771);
        }

        if (this.isDisabled) {
            FontHelper.renderFontCentered(sb, FontHelper.buttonLabelFont, this.buttonText,
                    this.current_x + TEXT_OFFSET_X, DRAW_Y + TEXT_OFFSET_Y, TEXT_DISABLED_COLOR);

        } else if (this.hb.clickStarted) {
            FontHelper.renderFontCentered(sb, FontHelper.buttonLabelFont, this.buttonText,
                    this.current_x + TEXT_OFFSET_X, DRAW_Y + TEXT_OFFSET_Y, Color.LIGHT_GRAY);

        } else if (this.hb.hovered) {
            FontHelper.renderFontCentered(sb, FontHelper.buttonLabelFont, this.buttonText,
                    this.current_x + TEXT_OFFSET_X, DRAW_Y + TEXT_OFFSET_Y, Settings.LIGHT_YELLOW_COLOR);

        } else {

            FontHelper.renderFontCentered(sb, FontHelper.buttonLabelFont, this.buttonText,
                    this.current_x + TEXT_OFFSET_X, DRAW_Y + TEXT_OFFSET_Y, Settings.LIGHT_YELLOW_COLOR);
        }

        renderControllerUi(sb);

        if (!this.isHidden) {
            this.hb.render(sb);
        }
    }

    private void renderShadow(SpriteBatch sb) {
        sb.draw(ImageMaster.CONFIRM_BUTTON_SHADOW, this.current_x - 256.0F, DRAW_Y - 128.0F, 256.0F, 128.0F, 512.0F,
                256.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 512, 256, false, false);
    }

    private void renderOutline(SpriteBatch sb) {
        sb.draw(ImageMaster.CONFIRM_BUTTON_OUTLINE, this.current_x - 256.0F, DRAW_Y - 128.0F, 256.0F, 128.0F, 512.0F,
                256.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 512, 256, false, false);
    }

    private void renderButton(SpriteBatch sb) {
        sb.draw(ImageMaster.CONFIRM_BUTTON, this.current_x - 256.0F, DRAW_Y - 128.0F, 256.0F, 128.0F, 512.0F, 256.0F,
                Settings.scale, Settings.scale, 0.0F, 0, 0, 512, 256, false, false);
    }

    private void renderControllerUi(SpriteBatch sb) {
        if (Settings.isControllerMode) {
            sb.setColor(Color.WHITE);
            sb.draw(CInputActionSet.select
                    .getKeyImg(), this.current_x - 32.0F - this.controller_offset_x + 96.0F * Settings.scale,
                    DRAW_Y - 32.0F + 57.0F * Settings.scale, 32.0F, 32.0F, 64.0F, 64.0F, Settings.scale, Settings.scale,
                    0.0F, 0, 0, 64, 64, false, false);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcraw\\ui\buttons\
 * ConfirmButton.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


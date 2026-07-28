package com.megacrit.cardcrawl.ui.buttons;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.unlock.AbstractUnlock;
import com.megacrit.cardcrawl.unlock.UnlockTracker;

public class UnlockConfirmButton {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("Unlock Confirm Button");
    public static final String[] TEXT = uiStrings.TEXT;

    private static final int W = 512;
    private static final int H = 256;
    private static final float TAKE_Y = Settings.HEIGHT / 2.0F - 410.0F * Settings.scale;
    private static final float X = Settings.WIDTH / 2.0F;
    private Color hoverColor = Color.WHITE.cpy();
    private Color textColor = Color.WHITE.cpy();
    private Color btnColor = Color.WHITE.cpy();
    private float target_a = 0.0F;
    private boolean done = false;
    private float animTimer = 0.0F;
    private static final float ANIM_TIME = 0.4F;
    private float scale = 0.8F;

    private static final float HOVER_BRIGHTNESS = 0.33F;

    private static final float SCALE_START = 0.6F;
    private String buttonText = "NOT_SET";

    private static final float HITBOX_W = 260.0F * Settings.scale;
    private static final float HITBOX_H = 80.0F * Settings.scale;
    public Hitbox hb = new Hitbox(0.0F, 0.0F, HITBOX_W, HITBOX_H);

    public UnlockConfirmButton() {
        this.buttonText = TEXT[0];
        this.hb.move(Settings.WIDTH / 2.0F, TAKE_Y);
    }

    public void update() {
        animateIn();

        if (!this.done && this.animTimer < 0.2F) {
            this.hb.update();
        }

        if (this.hb.hovered && !this.done) {
            this.hoverColor.a = 0.33F;
        } else {
            this.hoverColor.a = MathHelper.fadeLerpSnap(this.hoverColor.a, 0.0F);
        }

        if (this.hb.justHovered) {
            CardCrawlGame.sound.play("UI_HOVER");
        }

        if (this.hb.hovered && InputHelper.justClickedLeft) {
            this.hb.clickStarted = true;
            CardCrawlGame.sound.play("UI_CLICK_1");
        }

        if (this.hb.clicked || CInputActionSet.select.isJustPressed()) {
            CInputActionSet.select.unpress();
            this.hb.clicked = false;
            this.hb.hovered = false;

            if (AbstractDungeon.unlockScreen.unlock != null) {
                UnlockTracker.hardUnlock(AbstractDungeon.unlockScreen.unlock.key);
                CardCrawlGame.sound.stop("UNLOCK_SCREEN", AbstractDungeon.unlockScreen.id);

            } else if (AbstractDungeon.unlocks != null) {
                for (AbstractUnlock u : AbstractDungeon.unlocks) {
                    UnlockTracker.hardUnlock(u.key);
                }
            }

            InputHelper.justClickedLeft = false;
            hide();

            if (!AbstractDungeon.is_victory) {
                AbstractDungeon.previousScreen = AbstractDungeon.CurrentScreen.DEATH;
            } else {
                AbstractDungeon.previousScreen = AbstractDungeon.CurrentScreen.VICTORY;
            }
            AbstractDungeon.closeCurrentScreen();
        }

        this.textColor.a = MathHelper.fadeLerpSnap(this.textColor.a, this.target_a);
        this.btnColor.a = this.textColor.a;
    }

    private void animateIn() {
        if (this.animTimer != 0.0F) {
            this.animTimer -= Gdx.graphics.getDeltaTime();
            if (this.animTimer < 0.0F) {
                this.animTimer = 0.0F;
            }
            this.scale = Interpolation.elasticIn.apply(1.0F, 0.6F, this.animTimer / 0.4F);
        }
    }

    public void hide() {
        this.textColor = Color.LIGHT_GRAY.cpy();
        this.done = true;
    }

    public void show() {
        this.textColor = Color.WHITE.cpy();
        this.animTimer = 0.4F;
        this.hoverColor.a = 0.0F;
        this.textColor.a = 0.0F;
        this.target_a = 1.0F;
        this.scale = 0.6F;
        this.done = false;
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.btnColor);
        renderButton(sb);

        if (!this.hb.clickStarted && !this.done) {
            sb.setBlendFunction(770, 1);
            sb.setColor(this.hoverColor);
            renderButton(sb);
            sb.setBlendFunction(770, 771);
        }

        FontHelper.renderFontCentered(sb, FontHelper.buttonLabelFont, this.buttonText, Settings.WIDTH / 2.0F, TAKE_Y,
                this.textColor, this.scale);
    }

    private void renderButton(SpriteBatch sb) {
        if (this.hb.clickStarted) {
            sb.setColor(Color.LIGHT_GRAY);
        }
        if (!this.done) {
            sb.draw(ImageMaster.REWARD_SCREEN_TAKE_BUTTON, X - 256.0F, TAKE_Y - 128.0F, 256.0F, 128.0F, 512.0F, 256.0F,
                    this.scale * Settings.scale, this.scale * Settings.scale, 0.0F, 0, 0, 512, 256, false, false);

        } else {

            sb.draw(ImageMaster.REWARD_SCREEN_TAKE_USED_BUTTON, X - 256.0F, TAKE_Y - 128.0F, 256.0F, 128.0F, 512.0F,
                    256.0F, this.scale * Settings.scale, this.scale * Settings.scale, 0.0F, 0, 0, 512, 256, false,
                    false);
        }

        if (Settings.isControllerMode) {
            sb.draw(CInputActionSet.select
                    .getKeyImg(), X - 32.0F - 130.0F * Settings.scale, TAKE_Y - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F,
                    Settings.scale, Settings.scale, 0.0F, 0, 0, 64, 64, false, false);
        }

        this.hb.render(sb);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcraw\\ui\buttons\
 * UnlockConfirmButton.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


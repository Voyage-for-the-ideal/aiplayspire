package com.megacrit.cardcrawl.ui.buttons;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
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
import com.megacrit.cardcrawl.rewards.RewardItem;

public class SingingBowlButton {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("CardRewardScreen");
    public static final String[] TEXT = uiStrings.TEXT;

    private static final int W = 512;
    private static final int H = 256;
    private static final float SHOW_X = Settings.WIDTH / 2.0F + 165.0F * Settings.scale;
    private static final float HIDE_X = Settings.WIDTH / 2.0F;
    private float current_x = HIDE_X;
    private float target_x = this.current_x;
    private Color textColor = Color.WHITE.cpy();
    private Color btnColor = Color.WHITE.cpy();
    private boolean isHidden = true;
    private RewardItem rItem = null;

    private float controllerImgTextWidth = 0.0F;

    private static final float HITBOX_W = 260.0F * Settings.scale;
    private static final float HITBOX_H = 80.0F * Settings.scale;
    public Hitbox hb = new Hitbox(0.0F, 0.0F, HITBOX_W, HITBOX_H);

    public SingingBowlButton() {
        this.hb.move(Settings.WIDTH / 2.0F, SkipCardButton.TAKE_Y);
    }

    public void update() {
        if (this.isHidden) {
            return;
        }

        this.hb.update();

        if (this.hb.justHovered) {
            CardCrawlGame.sound.play("UI_HOVER");
        }

        if (this.hb.hovered && InputHelper.justClickedLeft) {
            this.hb.clickStarted = true;
            CardCrawlGame.sound.play("UI_CLICK_1");
        }

        if (this.hb.clicked || CInputActionSet.pageRightViewExhaust.isJustPressed()) {
            CInputActionSet.proceed.unpress();
            this.hb.clicked = false;
            onClick();
            AbstractDungeon.cardRewardScreen.closeFromBowlButton();
            AbstractDungeon.closeCurrentScreen();
            hide();
        }

        if (this.current_x != this.target_x) {
            this.current_x = MathUtils.lerp(this.current_x, this.target_x, Gdx.graphics.getDeltaTime() * 9.0F);
            if (Math.abs(this.current_x - this.target_x) < Settings.UI_SNAP_THRESHOLD) {
                this.current_x = this.target_x;
                this.hb.move(this.current_x, SkipCardButton.TAKE_Y);
            }
        }

        this.textColor.a = MathHelper.fadeLerpSnap(this.textColor.a, 1.0F);
        this.btnColor.a = this.textColor.a;
    }

    public void onClick() {
        AbstractDungeon.player.getRelic("Singing Bowl").flash();
        CardCrawlGame.sound.playA("SINGING_BOWL", MathUtils.random(-0.2F, 0.1F));
        AbstractDungeon.player.increaseMaxHp(2, true);
        AbstractDungeon.combatRewardScreen.rewards.remove(this.rItem);
    }

    public void hide() {
        if (!this.isHidden) {
            this.isHidden = true;
        }
    }

    public void show(RewardItem rItem) {
        this.isHidden = false;
        this.textColor.a = 0.0F;
        this.btnColor.a = 0.0F;
        this.current_x = HIDE_X;
        this.target_x = SHOW_X;
        this.rItem = rItem;
    }

    public void render(SpriteBatch sb) {
        if (this.isHidden) {
            return;
        }

        renderButton(sb);
        FontHelper.renderFontCentered(sb, FontHelper.buttonLabelFont, TEXT[2], this.current_x, SkipCardButton.TAKE_Y,
                this.textColor);
    }

    public boolean isHidden() {
        return this.isHidden;
    }

    private void renderButton(SpriteBatch sb) {
        sb.setColor(this.btnColor);
        sb.draw(ImageMaster.REWARD_SCREEN_TAKE_BUTTON, this.current_x - 256.0F, SkipCardButton.TAKE_Y - 128.0F, 256.0F,
                128.0F, 512.0F, 256.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 512, 256, false, false);

        if (this.hb.hovered && !this.hb.clickStarted) {
            sb.setBlendFunction(770, 1);
            sb.setColor(new Color(1.0F, 1.0F, 1.0F, 0.3F));
            sb.draw(ImageMaster.REWARD_SCREEN_TAKE_BUTTON, this.current_x - 256.0F, SkipCardButton.TAKE_Y - 128.0F,
                    256.0F, 128.0F, 512.0F, 256.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 512, 256, false, false);

            sb.setBlendFunction(770, 771);
        }

        if (Settings.isControllerMode) {
            if (this.controllerImgTextWidth == 0.0F) {
                this.controllerImgTextWidth = FontHelper.getSmartWidth(FontHelper.buttonLabelFont, TEXT[2], 9999.0F,
                        0.0F);
            }

            sb.setColor(Color.WHITE);
            sb.draw(CInputActionSet.pageRightViewExhaust
                    .getKeyImg(), this.current_x - 32.0F - this.controllerImgTextWidth / 2.0F - 38.0F * Settings.scale,
                    SkipCardButton.TAKE_Y - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, Settings.scale, Settings.scale, 0.0F, 0,
                    0, 64, 64, false, false);
        }

        this.hb.render(sb);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcraw\\ui\buttons\
 * SingingBowlButton.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


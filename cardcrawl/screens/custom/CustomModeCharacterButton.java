package com.megacrit.cardcrawl.screens.custom;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.ShaderHelper;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.CharacterStrings;

public class CustomModeCharacterButton {
    private CharacterStrings charStrings;
    private Texture buttonImg;
    public AbstractPlayer c;
    public boolean selected = false;
    public boolean locked = false;
    public Hitbox hb;
    private static final int W = 128;
    public float x;
    public float y;
    private Color highlightColor = new Color(1.0F, 0.8F, 0.2F, 0.0F);
    private float drawScale = 1.0F;

    public CustomModeCharacterButton(AbstractPlayer c, boolean locked) {
        this.buttonImg = c.getCustomModeCharacterButtonImage();
        this.charStrings = c.getCharacterString();
        this.hb = Settings.isMobile ? new Hitbox(120.0F * Settings.scale, 120.0F * Settings.scale)
                : new Hitbox(80.0F * Settings.scale, 80.0F * Settings.scale);

        this.drawScale = Settings.isMobile ? (Settings.scale * 1.2F) : Settings.scale;

        this.locked = locked;
        this.c = c;
    }

    public void move(float x, float y) {
        this.x = x;
        this.y = y;
        this.hb.move(x, y);
    }

    public void update(float x, float y) {
        this.x = x;
        this.y = y;
        this.hb.move(x, y);
        updateHitbox();
    }

    private void updateHitbox() {
        this.hb.update();
        if (this.hb.justHovered) {
            CardCrawlGame.sound.playA("UI_HOVER", -0.3F);
        }

        if (InputHelper.justClickedLeft && !this.locked && this.hb.hovered) {
            CardCrawlGame.sound.playA("UI_CLICK_1", -0.4F);
            this.hb.clickStarted = true;
        }

        if (this.hb.clicked) {
            this.hb.clicked = false;

            if (!this.selected) {
                CardCrawlGame.mainMenuScreen.customModeScreen.deselectOtherOptions(this);
                this.selected = true;
                CardCrawlGame.chosenCharacter = this.c.chosenClass;
                CardCrawlGame.mainMenuScreen.customModeScreen.confirmButton.isDisabled = false;
                CardCrawlGame.mainMenuScreen.customModeScreen.confirmButton.show();

                CardCrawlGame.sound.playA(this.c.getCustomModeCharacterButtonSoundKey(), MathUtils.random(-0.2F, 0.2F));
            }
        }
    }

    public void render(SpriteBatch sb) {
        renderOptionButton(sb);
        if (this.hb.hovered) {
            TipHelper.renderGenericTip(InputHelper.mX + 180.0F * Settings.scale, this.hb.cY + 40.0F * Settings.scale,
                    this.charStrings.NAMES[0], this.charStrings.TEXT[0]);
        }

        this.hb.render(sb);
    }

    private void renderOptionButton(SpriteBatch sb) {
        if (this.selected) {
            this.highlightColor.a = 0.25F
                    + (MathUtils.cosDeg((float) (System.currentTimeMillis() / 4L % 360L)) + 1.25F) / 3.5F;
            sb.setColor(this.highlightColor);
            sb.draw(ImageMaster.FILTER_GLOW_BG, this.hb.cX - 64.0F, this.hb.cY - 64.0F, 64.0F, 64.0F, 128.0F, 128.0F,
                    this.drawScale, this.drawScale, 0.0F, 0, 0, 128, 128, false, false);
        }

        if (this.locked) {
            ShaderHelper.setShader(sb, ShaderHelper.Shader.GRAYSCALE);
        } else if (this.selected || this.hb.hovered) {
            sb.setColor(Color.WHITE);
        } else {
            sb.setColor(Color.LIGHT_GRAY);
        }

        sb.draw(this.buttonImg, this.hb.cX - 64.0F, this.y - 64.0F, 64.0F, 64.0F, 128.0F, 128.0F, this.drawScale,
                this.drawScale, 0.0F, 0, 0, 128, 128, false, false);

        if (this.locked)
            ShaderHelper.setShader(sb, ShaderHelper.Shader.DEFAULT);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\custom\
 * CustomModeCharacterButton.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */


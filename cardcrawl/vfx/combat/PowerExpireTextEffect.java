package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class PowerExpireTextEffect extends AbstractGameEffect {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("PowerExpireTextEffect");
    public static final String[] TEXT = uiStrings.TEXT;

    private static final float TEXT_DURATION = 2.0F;
    private static final float STARTING_OFFSET_Y = 80.0F * Settings.scale;
    private static final float TARGET_OFFSET_Y = 160.0F * Settings.scale;

    private float x;
    private float y;
    private float offsetY;

    public PowerExpireTextEffect(float x, float y, String msg, TextureAtlas.AtlasRegion region) {
        this.duration = 2.0F;
        this.startingDuration = 2.0F;
        this.msg = msg;
        this.x = x - 64.0F * Settings.scale;
        this.y = y;
        this.color = Color.WHITE.cpy();
        this.offsetY = STARTING_OFFSET_Y;
        this.region = region;
        this.scale = Settings.scale * 0.7F;
    }
    private float h;
    private String msg;
    private boolean spikeEffectTriggered = false;
    private TextureAtlas.AtlasRegion region;

    public void update() {
        if (this.duration < this.startingDuration * 0.8F && !this.spikeEffectTriggered && !Settings.DISABLE_EFFECTS) {
            this.spikeEffectTriggered = true;
            int i;
            for (i = 0; i < 10; i++) {
                AbstractDungeon.effectsQueue.add(new FlyingSpikeEffect(this.x -

                        MathUtils.random(20.0F) * Settings.scale + 70.0F * Settings.scale,
                        this.y +
                                MathUtils.random(STARTING_OFFSET_Y, TARGET_OFFSET_Y) * Settings.scale,
                        0.0F,

                        MathUtils.random(50.0F, 400.0F) * Settings.scale, 0.0F, Settings.BLUE_TEXT_COLOR));
            }

            for (i = 0; i < 10; i++) {
                AbstractDungeon.effectsQueue.add(new FlyingSpikeEffect(this.x +

                        MathUtils.random(20.0F) * Settings.scale,
                        this.y +
                                MathUtils.random(STARTING_OFFSET_Y, TARGET_OFFSET_Y) * Settings.scale,
                        0.0F,

                        MathUtils.random(-400.0F, -50.0F) * Settings.scale, 0.0F, Settings.BLUE_TEXT_COLOR));
            }
        }

        this.offsetY = Interpolation.exp10In.apply(TARGET_OFFSET_Y, STARTING_OFFSET_Y, this.duration / 2.0F);
        this.color.a = Interpolation.exp10Out.apply(0.0F, 1.0F, this.duration / 2.0F);
        this.duration -= Gdx.graphics.getDeltaTime();

        if (this.duration < 0.0F) {
            this.isDone = true;
            this.duration = 0.0F;
        }
    }

    public void render(SpriteBatch sb) {
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.losePowerFont, this.msg, this.x, this.y + this.offsetY,
                this.color);
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.losePowerFont, TEXT[0], this.x,
                this.y + this.offsetY - 40.0F * Settings.scale, this.color);

        if (this.region != null) {
            sb.setColor(this.color);
            sb.setBlendFunction(770, 1);
            sb.draw((TextureRegion) this.region, this.x - (this.region.packedWidth / 2) - 64.0F * Settings.scale,
                    this.y + this.h + this.offsetY - (this.region.packedHeight / 2) - 30.0F * Settings.scale,
                    this.region.packedWidth / 2.0F, this.region.packedHeight / 2.0F, this.region.packedWidth,
                    this.region.packedHeight, this.scale, this.scale, this.rotation);

            sb.setBlendFunction(770, 771);
        }
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * PowerExpireTextEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

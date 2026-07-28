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
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.ScreenShake;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class IronWaveParticle
        extends AbstractGameEffect {
    private static final float EFFECT_DUR = 0.5F;
    private float x;
    private float y;

    public IronWaveParticle(float x, float y) {
        if (img == null) {
            img = ImageMaster.vfxAtlas.findRegion("combat/weightyImpact");
        }

        this.scale = Settings.scale;
        this.x = x - img.packedWidth / 2.0F;
        this.y = Settings.HEIGHT - img.packedHeight / 2.0F;
        this.duration = 0.5F;
        this.targetY = y - 180.0F * Settings.scale;

        this.rotation = 0.0F;
        this.color = new Color(1.0F, 1.0F, 0.1F, 0.0F);
    }
    private float targetY;
    private static TextureAtlas.AtlasRegion img;
    private boolean impactHook = false;
    public void update() {
        this.y = Interpolation.fade.apply(Settings.HEIGHT, this.targetY, 1.0F - this.duration / 0.5F);

        this.scale += Gdx.graphics.getDeltaTime();
        this.duration -= Gdx.graphics.getDeltaTime();

        if (this.duration < 0.0F) {
            this.isDone = true;
        } else if (this.duration < 0.2F) {
            if (!this.impactHook) {
                this.impactHook = true;

                CardCrawlGame.screenShake.shake(ScreenShake.ShakeIntensity.LOW, ScreenShake.ShakeDur.SHORT, false);
            }

            this.color.a = Interpolation.pow2Out.apply(0.0F, 1.0F, this.duration * 5.0F);
        } else {
            this.color.a = Interpolation.fade.apply(1.0F, 0.0F, this.duration / 0.5F);
        }
    }

    public void render(SpriteBatch sb) {
        sb.setBlendFunction(770, 1);
        this.color.g = 1.0F;
        sb.setColor(this.color);
        sb.draw((TextureRegion) img, this.x, this.y + 140.0F * Settings.scale, img.packedWidth / 2.0F,
                img.packedHeight / 2.0F, img.packedWidth / 2.0F, img.packedHeight * (this.duration + 0.2F) * 3.0F,
                this.scale *

                        MathUtils.random(0.99F, 1.01F) * 0.5F,
                this.scale *
                        MathUtils.random(0.99F, 1.01F) * 2.0F * (this.duration + 0.8F),
                this.rotation);

        this.color.g = 0.7F;
        sb.setColor(this.color);
        sb.draw((TextureRegion) img, this.x - 50.0F * Settings.scale, this.y + 140.0F * Settings.scale,
                img.packedWidth / 2.0F, img.packedHeight / 2.0F, img.packedWidth / 2.0F,
                img.packedHeight * (this.duration + 0.2F) * 2.0F, this.scale *

                        MathUtils.random(0.99F, 1.01F) * 0.6F,
                this.scale *
                        MathUtils.random(0.99F, 1.01F) * 2.0F * (this.duration + 0.8F),
                this.rotation);

        this.color.g = 0.5F;
        sb.setColor(this.color);
        sb.draw((TextureRegion) img, this.x - 100.0F * Settings.scale, this.y + 140.0F * Settings.scale,
                img.packedWidth / 2.0F, img.packedHeight / 2.0F, img.packedWidth,
                img.packedHeight * (this.duration + 0.2F) * 1.0F, this.scale *

                        MathUtils.random(0.99F, 1.01F) * 0.75F,
                this.scale *
                        MathUtils.random(0.99F, 1.01F) * 2.0F * (this.duration + 0.8F),
                this.rotation);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * IronWaveParticle.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

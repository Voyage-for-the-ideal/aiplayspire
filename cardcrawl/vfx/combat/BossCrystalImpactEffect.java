package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.ScreenShake;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class BossCrystalImpactEffect extends AbstractGameEffect {
    private TextureAtlas.AtlasRegion img;
    private float x;
    private float y;
    private static final float DUR = 0.5F;

    public BossCrystalImpactEffect(float x, float y) {
        CardCrawlGame.sound.playA("HEART_BEAT", MathUtils.random(0.0F, 0.6F));
        this.img = ImageMaster.CRYSTAL_IMPACT;
        this.x = x - (this.img.packedWidth / 2);
        this.y = y - (this.img.packedHeight / 2);
        this.color = Color.BLACK.cpy();
        this.duration = 0.5F;
        this.scale = 0.01F;

        CardCrawlGame.screenShake.shake(ScreenShake.ShakeIntensity.MED, ScreenShake.ShakeDur.SHORT, false);
    }

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
            this.duration = 0.0F;
        }
        this.color.a = Interpolation.pow3Out.apply(0.0F, 1.0F, this.duration / 2.0F);
        this.scale += Gdx.graphics.getDeltaTime() * 8.0F;
    }

    public void render(SpriteBatch sb) {
        sb.setBlendFunction(770, 1);
        sb.setColor(new Color(1.0F, 0.5F, 1.0F, this.color.a));
        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale *

                        MathUtils.random(0.8F, 1.2F),
                this.scale *
                        MathUtils.random(0.8F, 1.2F),
                this.rotation);

        sb.setBlendFunction(770, 771);
        sb.setColor(this.color);
        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale *

                        MathUtils.random(0.8F, 1.2F),
                this.scale *
                        MathUtils.random(0.8F, 1.2F),
                this.rotation);

        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale *

                        MathUtils.random(0.8F, 1.2F),
                this.scale *
                        MathUtils.random(0.8F, 1.2F),
                this.rotation);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * BossCrystalImpactEffect.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */

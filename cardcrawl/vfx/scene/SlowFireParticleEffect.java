package com.megacrit.cardcrawl.vfx.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class SlowFireParticleEffect extends AbstractGameEffect {
    private TextureAtlas.AtlasRegion img;
    private float x;
    private float y;

    public SlowFireParticleEffect() {
        setImg();
        this.renderBehind = true;
        this.startingDuration = 2.0F;
        this.duration = this.startingDuration;
        this.x = MathUtils.random(0.0F, Settings.WIDTH) - this.img.packedWidth / 2.0F;
        this.y = -this.img.packedHeight / 2.0F - 100.0F * Settings.scale;
        this.vX = MathUtils.random(-120.0F, 120.0F) * Settings.scale;
        this.vY2 = MathUtils.random(5.0F, 30.0F);
        this.vY2 *= this.vY2;
        this.vY2 *= Settings.scale;

        this.color = new Color(MathUtils.random(0.3F, 0.4F), MathUtils.random(0.3F, 0.7F), MathUtils.random(0.8F, 1.0F),
                0.0F);

        if (this.vX > 0.0F) {
            this.rotation = MathUtils.random(0.0F, -15.0F);
        } else {
            this.rotation = MathUtils.random(0.0F, 15.0F);
        }

        this.scale = MathUtils.random(0.3F, 3.0F) * Settings.scale;
    }
    private float vX;
    private float vY2;
    private float startingDuration;
    public void update() {
        this.x += this.vX * Gdx.graphics.getDeltaTime();
        this.y += this.vY2 * Gdx.graphics.getDeltaTime();

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        } else {
            this.color.a = Interpolation.pow2Out.apply(0.0F, 0.7F, this.duration);
        }
    }

    private void setImg() {
        if (MathUtils.randomBoolean()) {
            this.img = ImageMaster.GLOW_SPARK_2;
        } else {
            this.img = ImageMaster.GLOW_SPARK;
        }
    }

    public void render(SpriteBatch sb) {
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
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\scene\
 * SlowFireParticleEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

package com.megacrit.cardcrawl.vfx.combat;

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

public class IntenseZoomParticle
        extends AbstractGameEffect {
    private float x;
    private float y;
    private float flickerDuration = 0.0F;
    private TextureAtlas.AtlasRegion img;
    private Color color;
    private float offsetX;
    private float lengthX;
    private float lengthY;
    private boolean isBlack = false;

    public IntenseZoomParticle(float x, float y, boolean isBlack) {
        int i = MathUtils.random(2);
        if (i == 0) {
            this.img = ImageMaster.CONE_2;
        } else if (i == 1) {
            this.img = ImageMaster.CONE_4;
        } else {
            this.img = ImageMaster.CONE_5;
        }

        this.duration = 1.5F;

        this.isBlack = isBlack;
        if (isBlack) {
            this.color = Color.BLACK.cpy();
        } else {
            this.color = Settings.GOLD_COLOR.cpy();
        }

        this.x = x;
        this.y = y - this.img.packedHeight / 2.0F;
        randomize();
    }

    public void update() {
        this.flickerDuration -= Gdx.graphics.getDeltaTime();
        if (this.flickerDuration < 0.0F) {
            randomize();
            this.flickerDuration = MathUtils.random(0.0F, 0.05F);
        }

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void randomize() {
        this.rotation = MathUtils.random(360.0F);
        this.offsetX = MathUtils.random(200.0F, 600.0F) * Settings.scale * (2.0F - this.duration);
        this.lengthX = MathUtils.random(1.0F, 1.3F);
        this.lengthY = MathUtils.random(0.9F, 1.2F);
        if (this.isBlack) {
            this.color.a = MathUtils.random(0.5F, 1.0F) * Interpolation.pow2Out.apply(this.duration / 1.5F);
        } else {
            this.color.a = MathUtils.random(0.2F, 0.7F) * Interpolation.pow2Out.apply(this.duration / 1.5F);
        }
    }

    public void render(SpriteBatch sb) {
        if (!this.isBlack) {
            sb.setBlendFunction(770, 1);
        }

        sb.setColor(this.color);
        sb.draw((TextureRegion) this.img, this.x + this.offsetX, this.y, -this.offsetX, this.img.packedHeight / 2.0F,
                this.img.packedWidth * this.lengthX, this.img.packedHeight * this.lengthY, this.scale, this.scale,
                this.rotation);

        if (!this.isBlack)
            sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * IntenseZoomParticle.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

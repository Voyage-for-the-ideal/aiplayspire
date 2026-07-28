package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class FastSmokeParticle extends AbstractGameEffect {
    private float x;
    private float scale = 0.01F;
    private float y;
    private float vX;
    private float targetScale;
    private static TextureAtlas.AtlasRegion img;

    public FastSmokeParticle(float x, float y) {
        if (img == null) {
            img = ImageMaster.EXHAUST_L;
        }

        this.targetScale = MathUtils.random(0.3F, 0.6F) * Settings.scale;
        this.color = new Color(1.0F, MathUtils.random(0.8F, 1.0F), MathUtils.random(0.5F, 0.8F), 1.0F);

        this.x = x - img.packedWidth / 2.0F;
        this.y = y - img.packedHeight / 2.0F;
        this.rotation = MathUtils.random(360.0F);
        this.duration = 0.6F;
    }

    public void update() {
        if (this.color.b > 0.1F) {
            this.color.b -= Gdx.graphics.getDeltaTime() * 4.0F;
            this.color.g -= Gdx.graphics.getDeltaTime() * 3.0F;
            this.color.r -= Gdx.graphics.getDeltaTime() * 0.5F;
        } else if (this.color.g > 0.1F) {
            this.color.g -= Gdx.graphics.getDeltaTime() * 4.0F;
        } else if (this.color.r > 0.1F) {
            this.color.r -= Gdx.graphics.getDeltaTime() * 4.0F;
        }
        if (this.color.b < 0.0F) {
            this.color.b = 0.0F;
        }
        if (this.color.g < 0.0F) {
            this.color.g = 0.0F;
        }
        if (this.color.r < 0.0F) {
            this.color.r = 0.0F;
        }

        this.scale = Interpolation.swingIn.apply(this.targetScale, 0.1F, this.duration / 0.6F);
        this.rotation += this.vX * 2.0F * Gdx.graphics.getDeltaTime();
        this.color.a = this.duration;

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.draw((TextureRegion) img, this.x, this.y, img.packedWidth / 2.0F, img.packedHeight / 2.0F, img.packedWidth,
                img.packedHeight, this.scale, this.scale, this.rotation);
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * FastSmokeParticle.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

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

public class ExhaustPileParticle extends AbstractGameEffect {
    private float x;
    private float scale = 0.01F;
    private float y;
    private float vX;
    private float targetScale;
    private static TextureAtlas.AtlasRegion img;

    public ExhaustPileParticle(float x, float y) {
        if (img == null) {
            img = ImageMaster.EXHAUST_L;
        }

        this.targetScale = MathUtils.random(0.5F, 0.7F) * Settings.scale;
        this.color = new Color();
        this.color.a = 0.0F;
        this.color.g = MathUtils.random(0.2F, 0.4F);
        this.color.r = this.color.g + 0.1F;
        this.color.b = this.color.r + 0.1F;

        this.x = x - img.packedWidth / 2.0F;
        this.y = y - img.packedHeight / 2.0F;
        this.rotation = MathUtils.random(360.0F);
        this.startingDuration = 2.0F;
        this.duration = this.startingDuration;
    }

    public void update() {
        this.scale = Interpolation.bounceIn.apply(this.targetScale, 0.1F, this.duration / this.startingDuration);
        this.rotation += this.vX * this.startingDuration * Gdx.graphics.getDeltaTime();
        this.color.a = this.duration / this.startingDuration;

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
 * ExhaustPileParticle.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

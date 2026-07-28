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

public class SilentVictoryStarEffect extends AbstractGameEffect {
    private float x;
    private float y;
    private float vX;
    private float vY;
    private TextureAtlas.AtlasRegion img;

    public SilentVictoryStarEffect() {
        if (MathUtils.randomBoolean()) {
            this.img = ImageMaster.ROOM_SHINE_1;
            this.rotation = MathUtils.random(-5.0F, 5.0F);
        } else {
            this.img = ImageMaster.GLOW_SPARK_2;
        }

        this.x = MathUtils.random(-100.0F, 1870.0F) * Settings.xScale - this.img.packedWidth / 2.0F;
        float h = MathUtils.random(0.15F, 0.9F);
        this.y = Settings.HEIGHT * h;

        this.vX = MathUtils.random(12.0F, 20.0F) * Settings.scale;
        this.vY = MathUtils.random(-5.0F, 5.0F) * Settings.scale;
        this.color = new Color(MathUtils.random(0.55F, 0.6F), MathUtils.random(0.8F, 1.0F),
                MathUtils.random(0.95F, 1.0F), 0.0F);
        this.scale = h * MathUtils.random(1.5F, 1.8F) * Settings.scale;
    }

    public void update() {
        this.y += this.vY * Gdx.graphics.getDeltaTime();
        this.x += this.vX * Gdx.graphics.getDeltaTime();

        if (this.duration > this.startingDuration / 2.0F) {
            this.color.a = Interpolation.fade.apply(0.9F, 0.0F,
                    (this.duration - this.startingDuration / 2.0F) / this.startingDuration / 2.0F);
        } else {
            this.color.a = Interpolation.fade.apply(0.0F, 0.9F, this.duration / this.startingDuration / 2.0F);
        }

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale *

                        MathUtils.random(0.9F, 1.1F),
                this.scale *
                        MathUtils.random(0.8F, 1.3F),
                this.rotation);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\scene\
 * SilentVictoryStarEffect.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */

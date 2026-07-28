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

public class DeathScreenFloatyEffect extends AbstractGameEffect {
    private TextureAtlas.AtlasRegion img;
    private float x;
    private float y;
    private float vX;

    public DeathScreenFloatyEffect() {
        int roll = MathUtils.random(5);
        if (roll == 0) {
            this.img = ImageMaster.DEATH_VFX_1;
        } else if (roll == 1) {
            this.img = ImageMaster.DEATH_VFX_2;
        } else if (roll == 2) {
            this.img = ImageMaster.DEATH_VFX_3;
        } else if (roll == 3) {
            this.img = ImageMaster.DEATH_VFX_4;
        } else if (roll == 4) {
            this.img = ImageMaster.DEATH_VFX_5;
        } else {
            this.img = ImageMaster.DEATH_VFX_6;
        }

        this.x = MathUtils.random(0.0F, Settings.WIDTH) - this.img.packedWidth / 2.0F;
        this.y = MathUtils.random(0.0F, Settings.HEIGHT) - this.img.packedHeight / 2.0F;
        this.vX = MathUtils.random(-20.0F, 20.0F) * Settings.scale * this.scale;
        this.vY = MathUtils.random(-60.0F, 60.0F) * Settings.scale * this.scale;
        this.vX2 = MathUtils.random(-20.0F, 20.0F) * Settings.scale * this.scale;
        this.vY2 = MathUtils.random(-60.0F, 60.0F) * Settings.scale * this.scale;
        this.aV = MathUtils.random(-50.0F, 50.0F);

        float tmp = MathUtils.random(0.2F, 0.4F);
        this.color = new Color();
        this.color.r = tmp + MathUtils.random(0.0F, 0.2F);
        this.color.g = tmp;
        this.color.b = tmp + MathUtils.random(0.0F, 0.2F);
        this.renderBehind = MathUtils.randomBoolean(0.8F);
        this.scale = MathUtils.random(12.0F, 20.0F) * Settings.scale;
    }
    private float vY;
    private float vX2;
    private float vY2;
    private float aV;

    public void update() {
        this.x += this.vX * Gdx.graphics.getDeltaTime();
        this.y += this.vY * Gdx.graphics.getDeltaTime();
        this.vX += this.vX2 * Gdx.graphics.getDeltaTime();
        this.vY += this.vY2 * Gdx.graphics.getDeltaTime();
        this.rotation += this.aV * Gdx.graphics.getDeltaTime();

        if (this.startingDuration - this.duration < 1.5F) {
            this.color.a = Interpolation.fade.apply(0.0F, 0.3F, (this.startingDuration - this.duration) / 1.5F);
        } else if (this.duration < 1.5F) {
            this.color.a = Interpolation.fade.apply(0.3F, 0.0F, 1.0F - this.duration / 1.5F);
        }
        this.duration -= Gdx.graphics.getDeltaTime();

        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale, this.scale, this.rotation);
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * DeathScreenFloatyEffect.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */

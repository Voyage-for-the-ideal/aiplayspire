package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class WaterSplashParticleEffect
        extends AbstractGameEffect {
    private TextureAtlas.AtlasRegion img;
    private float x;
    private float y;

    public WaterSplashParticleEffect(float x, float y) {
        this.img = ImageMaster.DECK_GLOW_1;
        this.duration = MathUtils.random(0.5F, 1.0F);
        this.x = x - (this.img.packedWidth / 2) + MathUtils.random(-10.0F, 10.0F) * Settings.scale;
        this.y = y - (this.img.packedHeight / 2) - 40.0F * Settings.scale;
        this.color = new Color(1.0F, 0.2F, 0.1F, 0.0F);
        this.color.a = 0.0F;
        this.scale = MathUtils.random(1.5F, 3.5F) * Settings.scale;
        this.vX = MathUtils.random(-120.0F, 120.0F) * Settings.scale;
        this.vY = MathUtils.random(150.0F, 300.0F) * Settings.scale;
        this.floor = y - 40.0F * Settings.scale;
    }
    private float vX;
    private float vY;
    private float floor;
    public void update() {
        this.vY -= 1000.0F * Settings.scale * Gdx.graphics.getDeltaTime();
        this.x += this.vX * Gdx.graphics.getDeltaTime();
        this.y += this.vY * Gdx.graphics.getDeltaTime();
        Vector2 test = new Vector2(this.vX, this.vY);
        this.rotation = test.angle() + 45.0F;

        this.scale -= Gdx.graphics.getDeltaTime() / 2.0F;

        if (this.y < this.floor && this.vY < 0.0F &&
                this.duration > 0.2F) {
            this.duration = 0.2F;
        }

        if (this.duration < 0.2F) {
            this.color.a = Interpolation.fade.apply(0.0F, 1.0F, this.duration * 5.0F);
        } else {
            this.color.a = 1.0F;
        }

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale, this.scale * 0.54F, this.rotation);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * WaterSplashParticleEffect.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */

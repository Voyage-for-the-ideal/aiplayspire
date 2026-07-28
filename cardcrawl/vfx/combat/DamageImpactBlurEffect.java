package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class DamageImpactBlurEffect extends AbstractGameEffect {
    private float x;
    private float y;
    private float vX;

    public DamageImpactBlurEffect(float x, float y) {
        this.img = ImageMaster.STRIKE_BLUR;
        this.duration = MathUtils.random(0.5F, 0.75F);
        this.startingDuration = this.duration;
        this.x = x - this.img.packedWidth / 2.0F;
        this.y = y - this.img.packedHeight / 2.0F;
        this.rotation = 0.0F;
        this.vX = MathUtils.random(-42000.0F * Settings.scale, 42000.0F * Settings.scale);
        this.vY = MathUtils.random(-42000.0F * Settings.scale, 42000.0F * Settings.scale);
        this.startScale = MathUtils.random(4.0F, 8.0F);
        this.renderBehind = true;

        float tmp = MathUtils.random(0.1F, 0.3F);
        this.color = new Color(tmp, tmp, tmp, 1.0F);
    }
    private float vY;
    private float startScale;
    private TextureAtlas.AtlasRegion img;
    public void update() {
        this.x += this.vX * Gdx.graphics.getDeltaTime();
        this.y += this.vY * Gdx.graphics.getDeltaTime();
        this.vX *= 56.0F * Gdx.graphics.getDeltaTime();
        this.vY *= 56.0F * Gdx.graphics.getDeltaTime();

        this.scale = Settings.scale * (this.duration / this.startingDuration * 2.0F + this.startScale);
        this.color.a = this.duration;

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
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * DamageImpactBlurEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

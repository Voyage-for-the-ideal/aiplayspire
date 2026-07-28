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

public class DeckPoofParticle extends AbstractGameEffect {
    private float x;
    private float y;
    private float vX;
    private float scale = Settings.scale;
    private float vY;
    private float vA;
    private float delay;
    private boolean flipX = MathUtils.randomBoolean();
    private boolean flipY = MathUtils.randomBoolean();
    private TextureAtlas.AtlasRegion img;

    public DeckPoofParticle(float x, float y, boolean isDeck) {
        switch (MathUtils.random(2)) {
            case 0:
                this.img = ImageMaster.SMOKE_1;
                break;
            case 1:
                this.img = ImageMaster.SMOKE_2;
                break;
            default:
                this.img = ImageMaster.SMOKE_3;
                break;
        }

        this.duration = 0.8F;
        this.startingDuration = this.duration;
        this.delay = MathUtils.random(0.0F, 0.2F);
        float t = MathUtils.random(-10.0F, 10.0F) * MathUtils.random(-10.0F, 10.0F);
        this.x = x + t * Settings.scale - this.img.packedWidth / 2.0F;
        t = MathUtils.random(-10.0F, 10.0F) * MathUtils.random(-10.0F, 10.0F);
        this.y = y + t * Settings.scale - this.img.packedHeight / 2.0F;
        if (isDeck) {
            float rg = MathUtils.random(0.4F, 0.8F);
            this.color = new Color(rg + 0.1F, rg, rg - 0.2F, 0.0F);
            this.vA = MathUtils.random(-400.0F, 400.0F) * Settings.scale;
        } else {
            float rb = MathUtils.random(0.3F, 0.5F);
            this.color = new Color(rb, 0.35F, rb + 0.1F, 0.0F);
            this.vA = MathUtils.random(-70.0F, 70.0F) * MathUtils.random(-70.0F, 70.0F) * Settings.scale;
        }
        this.vX = MathUtils.random(-70.0F, 70.0F) * Settings.scale;
        this.vY = MathUtils.random(-100.0F, 300.0F) * Settings.scale;
        this.scale = MathUtils.random(0.3F, 1.8F) * Settings.scale;
        this.rotation = MathUtils.random(360.0F);
    }

    public void update() {
        if (this.delay > 0.0F) {
            this.delay -= Gdx.graphics.getDeltaTime();
            return;
        }
        this.rotation += this.vA * Gdx.graphics.getDeltaTime();
        this.x += this.vX * Gdx.graphics.getDeltaTime();
        this.y += this.vY * Gdx.graphics.getDeltaTime();
        this.vY *= 0.99F;
        this.vX *= 0.99F;
        this.scale += Gdx.graphics.getDeltaTime() / 2.0F;

        if (this.duration > this.startingDuration / 2.0F) {
            this.color.a = Interpolation.pow3Out.apply(0.0F, 1.0F, 1.0F - this.duration);
        } else {
            this.color.a = Interpolation.fade.apply(0.0F, 1.0F, this.duration * 2.0F);
        }

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        if (this.flipX && !this.img.isFlipX()) {
            this.img.flip(true, false);
        } else if (!this.flipX && this.img.isFlipX()) {
            this.img.flip(true, false);
        }
        if (this.flipY && !this.img.isFlipY()) {
            this.img.flip(false, true);
        } else if (!this.flipY && this.img.isFlipY()) {
            this.img.flip(false, true);
        }
        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale, this.scale, this.rotation);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * DeckPoofParticle.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

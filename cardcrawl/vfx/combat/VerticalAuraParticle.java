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

public class VerticalAuraParticle extends AbstractGameEffect {
    private float x;
    private float y;
    private float fadeInTimer = 0.2F;
    private float vY;
    private static final float FADE_IN_TIME = 0.2F;
    private static final float FADE_OUT_TIME = 0.8F;
    private float fadeOutTimer = 0.8F;
    private float stallTimer;
    private TextureAtlas.AtlasRegion img;

    public VerticalAuraParticle(Color c, float x, float y) {
        this.img = ImageMaster.VERTICAL_AURA;
        this.color = c.cpy();
        randomizeColor(this.color, 0.1F);
        this.color.a = 0.0F;
        this.x = x + MathUtils.random(-200.0F, 200.0F) * Settings.scale - this.img.packedWidth / 2.0F;
        this.y = y + MathUtils.random(-200.0F, 200.0F) * Settings.scale - this.img.packedHeight / 2.0F;
        this.vY = MathUtils.random(-300.0F, 300.0F) * Settings.scale;
        this.stallTimer = MathUtils.random(0.0F, 0.2F);
        this.scale = MathUtils.random(0.6F, 1.7F) * Settings.scale;
        this.renderBehind = true;
    }

    public void update() {
        if (this.stallTimer > 0.0F) {
            this.stallTimer -= Gdx.graphics.getDeltaTime();

            return;
        }

        this.y += this.vY * Gdx.graphics.getDeltaTime();

        if (this.fadeInTimer != 0.0F) {
            this.fadeInTimer -= Gdx.graphics.getDeltaTime();
            if (this.fadeInTimer < 0.0F) {
                this.fadeInTimer = 0.0F;
            }
            this.color.a = Interpolation.fade.apply(0.5F, 0.0F, this.fadeInTimer / 0.2F);
        } else if (this.fadeOutTimer != 0.0F) {
            this.fadeOutTimer -= Gdx.graphics.getDeltaTime();
            if (this.fadeOutTimer < 0.0F) {
                this.fadeOutTimer = 0.0F;
            }
            this.color.a = Interpolation.fade.apply(0.0F, 0.5F, this.fadeOutTimer / 0.8F);
        } else {
            this.isDone = true;
        }
    }

    private void randomizeColor(Color c, float amt) {
        float r = c.r + MathUtils.random(-amt, amt);
        float g = c.g + MathUtils.random(-amt, amt);
        float b = c.b + MathUtils.random(-amt, amt);

        if (r > 1.0F) {
            r = 1.0F;
        } else if (r < 0.0F) {
            r = 0.0F;
        }

        if (g > 1.0F) {
            g = 1.0F;
        } else if (g < 0.0F) {
            g = 0.0F;
        }

        if (b > 1.0F) {
            b = 1.0F;
        } else if (b < 0.0F) {
            b = 0.0F;
        }

        c.r = r;
        c.g = g;
        c.b = b;
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
 * VerticalAuraParticle.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

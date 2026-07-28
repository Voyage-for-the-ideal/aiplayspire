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

public class CardPoofParticle extends AbstractGameEffect {
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

    public CardPoofParticle(float x, float y) {
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

        this.duration = 0.6F;
        this.startingDuration = this.duration;
        this.delay = MathUtils.random(0.0F, 0.1F);
        float t = MathUtils.random(-160.0F, 160.0F);
        this.x = x + t * Settings.scale - this.img.packedWidth / 2.0F;
        t = MathUtils.random(-180.0F, 180.0F);
        this.y = y + t * Settings.scale - this.img.packedHeight / 2.0F;
        float rg = MathUtils.random(0.4F, 0.8F);
        this.color = new Color(rg + 0.05F, rg, rg + 0.05F, 0.0F);
        this.vA = MathUtils.random(-400.0F, 400.0F) * Settings.scale;
        this.vX = MathUtils.random(-170.0F, 170.0F) * Settings.scale;
        this.vY = MathUtils.random(-170.0F, 170.0F) * Settings.scale;
        this.scale = MathUtils.random(0.8F, 2.5F) * Settings.scale;
        this.rotation = MathUtils.random(360.0F);
        this.renderBehind = true;
    }

    public void update() {
        if (this.delay > 0.0F) {
            this.delay -= Gdx.graphics.getDeltaTime();
            return;
        }
        this.rotation += this.vA * Gdx.graphics.getDeltaTime();
        this.x += this.vX * Gdx.graphics.getDeltaTime();
        this.y += this.vY * Gdx.graphics.getDeltaTime();
        this.scale += Gdx.graphics.getDeltaTime() * 5.0F;

        if (this.duration > this.startingDuration / 2.0F) {
            this.color.a = Interpolation.pow3Out.apply(0.0F, 0.7F, 1.0F - this.duration);
        } else {
            this.color.a = Interpolation.fade.apply(0.0F, 0.7F, this.duration * 2.0F);
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
 * CardPoofParticle.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

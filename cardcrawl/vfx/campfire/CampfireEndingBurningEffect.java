package com.megacrit.cardcrawl.vfx.campfire;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class CampfireEndingBurningEffect extends AbstractGameEffect {
    private TextureAtlas.AtlasRegion img;
    private float x;
    private float y;
    private boolean flipX = MathUtils.randomBoolean();
    private float vX;
    private float vY2;
    private float vY;
    private float startingDuration;
    private float delayTimer = MathUtils.random(0.1F);

    public CampfireEndingBurningEffect() {
        setImg();
        this.startingDuration = 1.0F;
        this.duration = this.startingDuration;
        this.x = MathUtils.random(0.0F, Settings.WIDTH) - this.img.packedWidth / 2.0F;
        this.y = -this.img.packedHeight / 2.0F - 100.0F * Settings.scale;
        this.vX = MathUtils.random(-120.0F, 120.0F) * Settings.scale;
        this.vY = 0.0F;
        this.vY2 = MathUtils.random(1500.0F, 3000.0F) * Settings.scale;
        this.vY2 -= Math.abs(this.x - 1485.0F * Settings.scale) / 2.0F;

        this.color = new Color(1.0F, MathUtils.random(0.5F, 0.9F), MathUtils.random(0.2F, 0.5F), 0.0F);

        if (this.vX > 0.0F) {
            this.rotation = MathUtils.random(0.0F, -15.0F);
        } else {
            this.rotation = MathUtils.random(0.0F, 15.0F);
        }

        this.scale = MathUtils.random(1.5F, 4.0F) * Settings.scale;
    }

    public void update() {
        if (this.delayTimer > 0.0F) {
            this.delayTimer -= Gdx.graphics.getDeltaTime();
            return;
        }
        this.x += this.vX * Gdx.graphics.getDeltaTime();
        this.y += this.vY * Gdx.graphics.getDeltaTime();
        this.vY = MathHelper.slowColorLerpSnap(this.vY, this.vY2);
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        } else {
            this.color.a = Interpolation.pow2Out.apply(0.0F, 0.8F, this.duration);
        }
    }

    private void setImg() {
        int roll = MathUtils.random(2);
        if (roll == 0) {
            this.img = ImageMaster.GLOW_SPARK;
        } else if (roll == 1) {
            this.img = ImageMaster.GLOW_SPARK;
        } else {
            this.img = ImageMaster.GLOW_SPARK_2;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setBlendFunction(770, 1);
        sb.setColor(this.color);
        if (this.flipX && !this.img.isFlipX()) {
            this.img.flip(true, false);
        } else if (!this.flipX && this.img.isFlipX()) {
            this.img.flip(true, false);
        }

        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale, this.scale, this.rotation);

        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale *

                        MathUtils.random(0.8F, 1.2F),
                this.scale *
                        MathUtils.random(0.8F, 1.2F),
                this.rotation);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\campfire\
 * CampfireEndingBurningEffect.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */

package com.megacrit.cardcrawl.vfx.campfire;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class CampfireBurningEffect
        extends AbstractGameEffect {
    private TextureAtlas.AtlasRegion img;
    private float brightness;
    private float x;
    private float y;
    private boolean flipX = MathUtils.randomBoolean();
    private float vX;
    private float vY2;
    private float vY;
    private float startingDuration;
    private float delayTimer = MathUtils.random(0.1F);

    public CampfireBurningEffect() {
        setImg();
        this.startingDuration = 1.75F;
        this.duration = this.startingDuration;
        this.x = MathUtils.random(1200.0F, 1770.0F) * Settings.xScale - this.img.packedWidth / 2.0F;
        this.y = Settings.HEIGHT / 2.0F - 200.0F * Settings.yScale - this.img.packedHeight / 2.0F;
        this.vX = MathUtils.random(-70.0F, 70.0F) * Settings.xScale;
        this.vY = 0.0F;
        this.vY2 = MathUtils.random(250.0F, 450.0F) * Settings.scale;
        this.vY2 -= Math.abs(this.x - 1485.0F * Settings.scale) / 2.0F;

        if (CardCrawlGame.dungeon instanceof com.megacrit.cardcrawl.dungeons.TheBeyond) {
            this.color = new Color(0.0F, 1.0F, 1.0F, 0.0F);
            this.color.g -= MathUtils.random(0.4F);
            this.color.b -= MathUtils.random(0.4F);
        } else if (CardCrawlGame.dungeon instanceof com.megacrit.cardcrawl.dungeons.TheCity) {
            this.color = new Color(1.0F, 1.0F, 1.0F, 0.0F);
            this.color.r -= MathUtils.random(0.5F);
            this.color.b -= this.color.r - MathUtils.random(0.0F, 0.2F);
        } else {
            this.color = new Color(1.0F, 1.0F, 1.0F, 0.0F);
            this.color.g -= MathUtils.random(0.5F);
            this.color.b -= this.color.g - MathUtils.random(0.0F, 0.2F);
        }

        this.rotation = MathUtils.random(-10.0F, 10.0F);
        this.scale = MathUtils.random(3.0F, 4.0F);
        this.brightness = MathUtils.random(0.2F, 0.6F);
    }

    public void update() {
        if (this.delayTimer > 0.0F) {
            this.delayTimer -= Gdx.graphics.getDeltaTime();
            return;
        }
        this.x += this.vX * Gdx.graphics.getDeltaTime();
        this.y += this.vY * Gdx.graphics.getDeltaTime();
        this.vY = MathHelper.slowColorLerpSnap(this.vY, this.vY2);
        this.scale *= 1.0F - Gdx.graphics.getDeltaTime() / 10.0F;

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        } else if (this.startingDuration - this.duration < 0.75F) {
            this.color.a = Interpolation.fade.apply(0.0F, this.brightness,
                    (this.startingDuration - this.duration) / 0.75F);
        } else if (this.duration < 1.0F) {
            this.color.a = Interpolation.fade.apply(0.0F, this.brightness, this.duration / 1.0F);
        }
    }

    private void setImg() {
        int roll = MathUtils.random(2);
        if (roll == 0) {
            this.img = ImageMaster.FLAME_1;
        } else if (roll == 1) {
            this.img = ImageMaster.FLAME_2;
        } else {
            this.img = ImageMaster.FLAME_3;
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
                this.img.packedWidth, this.img.packedHeight, this.scale * Settings.scale, this.scale * Settings.scale,
                this.rotation);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\campfire\
 * CampfireBurningEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

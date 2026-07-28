package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.ShineSparkleEffect;

public class FlickCoinEffect extends AbstractGameEffect {
    private static TextureAtlas.AtlasRegion img;
    private float sX;
    private float sY;
    private float cX;
    private float cY;
    private float sparkleTimer = 0.0F;
    private float dX;
    private float dY;
    private float yOffset;
    private float bounceHeight;
    private static final float DUR = 0.5F;
    private boolean playedSfx = false;

    public FlickCoinEffect(float srcX, float srcY, float destX, float destY) {
        if (img == null) {
            img = ImageMaster.vfxAtlas.findRegion("combat/empowerCircle1");
        }

        this.sX = srcX;
        this.sY = srcY;
        this.cX = this.sX;
        this.cY = this.sY;
        this.dX = destX;
        this.dY = destY - 100.0F * Settings.scale;
        this.rotation = 0.0F;
        this.duration = 0.5F;

        this.color = new Color(1.0F, 1.0F, 0.0F, 0.0F);

        if (this.sY > this.dY) {
            this.bounceHeight = 600.0F * Settings.scale;
        } else {
            this.bounceHeight = this.dY - this.sY + 600.0F * Settings.scale;
        }
    }

    public void update() {
        if (!this.playedSfx) {
            this.playedSfx = true;
            CardCrawlGame.sound.playA("ATTACK_WHIFF_2", MathUtils.random(0.7F, 0.8F));
        }

        this.sparkleTimer -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.4F && this.sparkleTimer < 0.0F) {
            for (int i = 0; i < MathUtils.random(2, 5); i++) {
                AbstractDungeon.effectsQueue.add(new ShineSparkleEffect(this.cX, this.cY + this.yOffset));
            }
            this.sparkleTimer = MathUtils.random(0.05F, 0.1F);
        }

        this.cX = Interpolation.linear.apply(this.dX, this.sX, this.duration / 0.5F);
        this.cY = Interpolation.linear.apply(this.dY, this.sY, this.duration / 0.5F);

        if (this.dX > this.sX) {
            this.rotation -= Gdx.graphics.getDeltaTime() * 1000.0F;
        } else {
            this.rotation += Gdx.graphics.getDeltaTime() * 1000.0F;
        }

        if (this.duration > 0.25F) {
            this.color.a = Interpolation.exp5In.apply(1.0F, 0.0F, (this.duration - 0.25F) / 0.2F) * Settings.scale;
            this.yOffset = Interpolation.circleIn.apply(this.bounceHeight, 0.0F, (this.duration - 0.25F) / 0.25F)
                    * Settings.scale;
        } else {
            this.yOffset = Interpolation.circleOut.apply(0.0F, this.bounceHeight, this.duration / 0.25F)
                    * Settings.scale;
        }

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
            CardCrawlGame.sound.playA("GOLD_GAIN", MathUtils.random(0.0F, 0.1F));
            AbstractDungeon.effectsQueue
                    .add(new AdditiveSlashImpactEffect(this.dX, this.dY + 100.0F * Settings.scale, Color.GOLD
                            .cpy()));
        }
    }

    public void render(SpriteBatch sb) {
        sb.setBlendFunction(770, 1);
        sb.setColor(new Color(0.4F, 1.0F, 1.0F, this.color.a / 5.0F));

        sb.setColor(this.color);
        sb.draw((TextureRegion) img, this.cX - (img.packedWidth / 2), this.cY - (img.packedHeight / 2) + this.yOffset,
                img.packedWidth / 2.0F, img.packedHeight / 2.0F, img.packedWidth, img.packedHeight, this.scale * 0.7F,
                this.scale * 0.4F, this.rotation);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * FlickCoinEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

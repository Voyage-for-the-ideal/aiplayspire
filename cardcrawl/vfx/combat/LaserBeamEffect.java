package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.BorderLongFlashEffect;

public class LaserBeamEffect
        extends AbstractGameEffect {
    private float x;
    private float y;

    public LaserBeamEffect(float x, float y) {
        if (img == null) {
            img = ImageMaster.vfxAtlas.findRegion("combat/laserThick");
        }

        this.x = x;
        this.y = y;

        this.color = Color.CYAN.cpy();
        this.duration = 2.0F;
        this.startingDuration = 2.0F;
    }
    private static final float DUR = 2.0F;
    private static TextureAtlas.AtlasRegion img;
    private boolean playedSfx = false;

    public void update() {
        if (!this.playedSfx) {
            AbstractDungeon.effectsQueue.add(new BorderLongFlashEffect(Color.SKY));
            this.playedSfx = true;
            CardCrawlGame.sound.play("ATTACK_MAGIC_BEAM");
            CardCrawlGame.screenShake.rumble(2.0F);
        }
        this.duration -= Gdx.graphics.getDeltaTime();

        if (this.duration > this.startingDuration / 2.0F) {
            this.color.a = Interpolation.pow2In.apply(1.0F, 0.0F, this.duration - 1.0F);
        } else {
            this.color.a = Interpolation.pow2Out.apply(0.0F, 1.0F, this.duration);
        }

        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setBlendFunction(770, 1);
        sb.setColor(new Color(0.5F, 0.7F, 1.0F, this.color.a));
        sb.draw((TextureRegion) img, this.x, this.y - (img.packedHeight / 2), 0.0F, img.packedHeight / 2.0F,
                img.packedWidth, img.packedHeight, this.scale * 2.0F +

                        MathUtils.random(-0.05F, 0.05F),
                this.scale * 1.5F +
                        MathUtils.random(-0.1F, 0.1F),
                MathUtils.random(186.0F, 189.0F));
        sb.draw((TextureRegion) img, this.x, this.y - (img.packedHeight / 2), 0.0F, img.packedHeight / 2.0F,
                img.packedWidth, img.packedHeight, this.scale * 2.0F +

                        MathUtils.random(-0.05F, 0.05F),
                this.scale * 1.5F +
                        MathUtils.random(-0.1F, 0.1F),
                MathUtils.random(186.0F, 189.0F));
        sb.setColor(this.color);
        sb.draw((TextureRegion) img, this.x, this.y - (img.packedHeight / 2), 0.0F, img.packedHeight / 2.0F,
                img.packedWidth, img.packedHeight, this.scale * 2.0F, this.scale / 2.0F,

                MathUtils.random(187.0F, 188.0F));
        sb.draw((TextureRegion) img, this.x, this.y - (img.packedHeight / 2), 0.0F, img.packedHeight / 2.0F,
                img.packedWidth, img.packedHeight, this.scale * 2.0F, this.scale / 2.0F,

                MathUtils.random(187.0F, 188.0F));
        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * LaserBeamEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

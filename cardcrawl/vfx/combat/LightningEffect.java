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
import com.megacrit.cardcrawl.helpers.ScreenShake;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class LightningEffect extends AbstractGameEffect {
    private float x;
    private float y;
    private TextureAtlas.AtlasRegion img = null;

    public LightningEffect(float x, float y) {
        if (this.img == null) {
            this.img = ImageMaster.vfxAtlas.findRegion("combat/lightning");
        }

        this.x = x - this.img.packedWidth / 2.0F;
        this.y = y;
        this.color = Color.WHITE.cpy();
        this.duration = 0.5F;
        this.startingDuration = 0.5F;
    }

    public void update() {
        if (this.duration == this.startingDuration) {
            CardCrawlGame.screenShake.shake(ScreenShake.ShakeIntensity.LOW, ScreenShake.ShakeDur.MED, false);
            for (int i = 0; i < 15; i++) {
                AbstractDungeon.topLevelEffectsQueue.add(new ImpactSparkEffect(this.x +

                        MathUtils.random(-20.0F, 20.0F) * Settings.scale + 150.0F * Settings.scale,
                        this.y +
                                MathUtils.random(-20.0F, 20.0F) * Settings.scale));
            }
        }

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }

        this.color.a = Interpolation.bounceIn.apply(this.duration * 2.0F);
    }

    public void render(SpriteBatch sb) {
        sb.setBlendFunction(770, 1);
        sb.setColor(this.color);
        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, 0.0F, this.img.packedWidth,
                this.img.packedHeight, this.scale, this.scale, this.rotation);
        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * LightningEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

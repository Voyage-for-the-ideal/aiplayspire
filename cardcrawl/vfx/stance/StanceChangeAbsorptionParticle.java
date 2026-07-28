package com.megacrit.cardcrawl.vfx.stance;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class StanceChangeAbsorptionParticle extends AbstractGameEffect {
    private float oX;
    private float oY;
    private float x;

    public StanceChangeAbsorptionParticle(Color color, float x, float y) {
        this.startingDuration = 1.0F;
        this.duration = this.startingDuration;
        this.color = color.cpy();
        this.color.r -= MathUtils.random(0.1F);
        this.color.g -= MathUtils.random(0.1F);
        this.color.b -= MathUtils.random(0.1F);

        this.rotation = MathUtils.random(360.0F);
        this.oX = x;
        this.oY = y;
        this.distOffset = MathUtils.random(-200.0F, 1000.0F);
        this.renderBehind = true;
        this.aV = MathUtils.random(50.0F, 80.0F);
        this.scaleOffset = MathUtils.random(1.0F);
    }
    private float y;
    private float aV;
    private float distOffset;
    private float scaleOffset;
    public void update() {
        this.x = this.oX
                + MathUtils.cosDeg(this.rotation) * (800.0F + this.distOffset) * this.duration * Settings.scale;
        this.y = this.oY
                + MathUtils.sinDeg(this.rotation) * (800.0F + this.distOffset) * this.duration * Settings.scale;
        this.duration -= Gdx.graphics.getDeltaTime();
        this.rotation -= this.duration * Interpolation.pow5Out.apply(this.aV, 2.0F, this.duration);
        this.scale = (this.duration + 0.2F + this.scaleOffset) * Settings.scale;
        this.color.a = Interpolation.pow2Out.apply(0.0F, 1.0F, this.duration);
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.setBlendFunction(770, 1);
        sb.draw(ImageMaster.WOBBLY_ORB_VFX, this.x - 16.0F, this.y - 16.0F, 16.0F, 16.0F, 32.0F, 32.0F, this.scale *

                MathUtils.random(0.5F, 2.0F),
                this.scale *
                        MathUtils.random(0.5F, 2.0F),
                this.rotation - 200.0F, 0, 0, 32, 32, false, false);

        sb.draw(ImageMaster.WOBBLY_ORB_VFX, this.x - 16.0F, this.y - 16.0F, 16.0F, 16.0F, 32.0F, 32.0F, this.scale *

                MathUtils.random(0.6F, 2.5F),
                this.scale *
                        MathUtils.random(0.6F, 2.5F),
                this.rotation - 200.0F, 0, 0, 32, 32, false, false);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\stance\
 * StanceChangeAbsorptionParticle.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */

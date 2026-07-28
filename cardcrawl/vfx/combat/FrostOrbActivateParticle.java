package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class FrostOrbActivateParticle extends AbstractGameEffect {
    public static final int W = 64;
    private Texture img = null;
    private float cX;
    private float cY;
    private float sX;

    public FrostOrbActivateParticle(int index, float x, float y) {
        this.cX = x;
        this.cY = y;
        this.sX = this.cX;
        this.sY = this.cY;

        this.rotation = MathUtils.random(-5.0F, 5.0F);
        switch (index) {
            case 0:
                this.tX = this.sX;
                this.tY = this.sY + 5.0F * Settings.scale;
                this.img = ImageMaster.FROST_ACTIVATE_VFX_1;
                this.tRotation = MathUtils.random(-5.0F, 5.0F);
                break;
            case 1:
                this.tX = this.sX - 10.0F * Settings.scale;
                this.tY = this.sY - 5.0F * Settings.scale;
                this.img = ImageMaster.FROST_ACTIVATE_VFX_2;
                this.tRotation = this.rotation + MathUtils.random(-30.0F, 30.0F);
                break;
            default:
                this.tX = this.sX + 10.0F * Settings.scale;
                this.tY = this.sY - 5.0F * Settings.scale;
                this.tRotation = this.rotation - MathUtils.random(-30.0F, 30.0F);
                this.img = ImageMaster.FROST_ACTIVATE_VFX_3;
                break;
        }

        this.renderBehind = false;
        this.color = new Color(0.5F, 0.95F, 1.0F, 0.9F);
        this.scale = 2.0F * Settings.scale;
        this.startingDuration = 0.3F;
        this.duration = this.startingDuration;
    }
    private float sY;
    private float tX;

    public void update() {
        this.color.a = Interpolation.pow2Out.apply(0.2F, 0.9F, this.duration / this.startingDuration);
        if (this.color.a < 0.0F) {
            this.color.a = 0.0F;
        }

        this.cX = Interpolation.swingIn.apply(this.tX, this.sX, this.duration / this.startingDuration);
        this.cY = Interpolation.swingIn.apply(this.tY, this.sY, this.duration / this.startingDuration);
        this.rotation = Interpolation.swingIn.apply(this.tRotation, 0.0F, this.duration / this.startingDuration);
        this.scale = Interpolation.fade.apply(2.4F, 2.0F, this.duration / this.startingDuration) * Settings.scale;

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F)
            this.isDone = true;
    }
    private float tY;
    private float tRotation;

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.setBlendFunction(770, 1);
        sb.draw(this.img, this.cX - 32.0F, this.cY - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale, this.scale,
                this.rotation, 0, 0, 64, 64, false, false);
        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * FrostOrbActivateParticle.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */

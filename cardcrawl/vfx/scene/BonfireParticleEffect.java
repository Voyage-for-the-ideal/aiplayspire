package com.megacrit.cardcrawl.vfx.scene;

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

public class BonfireParticleEffect
        extends AbstractGameEffect {
    private float effectDuration;
    private float x;
    private float y;

    public BonfireParticleEffect(boolean isAbove, boolean isBlue) {
        this.img = getImg();
        this.x = 170.0F * Settings.scale + MathUtils.random(-25.0F, 25.0F) * Settings.scale;
        this.y = 44.0F * Settings.scale;
        this.effectDuration = MathUtils.random(1.0F, 2.0F);
        this.duration = this.effectDuration;
        this.startingDuration = this.effectDuration;
        this.vY = MathUtils.random(0.0F, 200.0F) * Settings.scale;
        this.vX = MathUtils.random(-30.0F, 30.0F) * Settings.scale;

        this.rotation = MathUtils.random(-10.0F, 10.0F);
        this.scale = MathUtils.random(0.8F, 2.5F);

        this.vY /= this.scale;
        this.vX /= this.scale * 2.0F;
        int roll = MathUtils.random(2);

        if (!isBlue) {
            if (roll == 0) {
                this.color = Color.ORANGE.cpy();
            } else if (roll == 1) {
                this.color = Color.GOLDENROD.cpy();
            } else {
                this.color = Color.CORAL.cpy();
            }

        } else if (roll == 0) {
            this.color = Color.CYAN.cpy();
        } else if (roll == 1) {
            this.color = Color.SKY.cpy();
        } else {
            this.color = Color.TEAL.cpy();
        }

        this.rotator = MathUtils.random(-10.0F, 10.0F);
    }
    private float vY;
    private float vX;
    private float rotator;
    private TextureAtlas.AtlasRegion img;
    private TextureAtlas.AtlasRegion getImg() {
        switch (MathUtils.random(0, 2)) {
            case 0:
                return ImageMaster.TORCH_FIRE_1;
            case 1:
                return ImageMaster.TORCH_FIRE_2;
        }
        return ImageMaster.TORCH_FIRE_3;
    }

    public void update() {
        this.rotation += this.rotator * Gdx.graphics.getDeltaTime();
        this.y += this.vY * Gdx.graphics.getDeltaTime();
        this.x += this.vX * Gdx.graphics.getDeltaTime();
        this.vX *= 0.995F;

        this.duration -= Gdx.graphics.getDeltaTime();

        if (this.duration > this.startingDuration / 2.0F) {
            this.color.a = Interpolation.exp10In.apply(0.4F, 0.0F,
                    (this.duration - this.startingDuration / 2.0F) / this.startingDuration / 2.0F);

        } else {

            this.color.a = Interpolation.pow2In.apply(0.0F, 0.4F, this.duration / this.startingDuration / 2.0F);
        }

        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb, float x2, float y2) {
        sb.setColor(this.color);
        sb.setBlendFunction(770, 1);
        sb.draw((TextureRegion) this.img, this.x + x2, this.y + y2, this.img.packedWidth / 2.0F,
                this.img.packedHeight / 2.0F, this.img.packedWidth, this.img.packedHeight, this.scale * Settings.scale,
                this.scale * Settings.scale, this.rotation);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }

    public void render(SpriteBatch sb) {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\scene\
 * BonfireParticleEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

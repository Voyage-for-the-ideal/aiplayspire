package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class GiantEyeEffect
        extends AbstractGameEffect {
    private TextureAtlas.AtlasRegion img;

    public GiantEyeEffect(float setX, float setY, Color setColor) {
        this.duration = 1.0F;
        this.startingDuration = 1.0F;
        this.color = setColor.cpy();
        this.color.a = 0.0F;
        this.img = ImageMaster.EYE_ANIM_0;
        this.x = setX - this.img.packedWidth / 2.0F;
        this.y = setY - this.img.packedHeight / 2.0F;
    }
    private float x;
    private float y;
    public void update() {
        if (1.0F - this.duration < 0.1F) {
            this.color.a = Interpolation.fade.apply(0.0F, 0.9F, (1.0F - this.duration) * 10.0F);
        } else {
            this.color.a = Interpolation.pow2Out.apply(0.0F, 0.9F, this.duration);
        }

        if (this.duration > this.startingDuration * 0.85F) {
            this.img = ImageMaster.EYE_ANIM_0;
        } else if (this.duration > this.startingDuration * 0.8F) {
            this.img = ImageMaster.EYE_ANIM_1;
        } else if (this.duration > this.startingDuration * 0.75F) {
            this.img = ImageMaster.EYE_ANIM_2;
        } else if (this.duration > this.startingDuration * 0.7F) {
            this.img = ImageMaster.EYE_ANIM_3;
        } else if (this.duration > this.startingDuration * 0.65F) {
            this.img = ImageMaster.EYE_ANIM_4;
        } else if (this.duration > this.startingDuration * 0.6F) {
            this.img = ImageMaster.EYE_ANIM_5;
        } else if (this.duration > this.startingDuration * 0.55F) {
            this.img = ImageMaster.EYE_ANIM_6;
        } else if (this.duration > this.startingDuration * 0.38F) {
            this.img = ImageMaster.EYE_ANIM_5;
        }

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setBlendFunction(770, 1);
        sb.setColor(this.color);
        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, (this.scale +

                        MathUtils.random(-0.02F, 0.02F)) * 3.0F,
                (this.scale +
                        MathUtils.random(-0.03F, 0.03F)) * 3.0F,
                this.rotation);

        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, (this.scale +

                        MathUtils.random(-0.02F, 0.02F)) * 3.0F,
                (this.scale +
                        MathUtils.random(-0.03F, 0.03F)) * 3.0F,
                this.rotation);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * GiantEyeEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

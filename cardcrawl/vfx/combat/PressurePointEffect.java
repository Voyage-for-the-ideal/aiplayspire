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

public class PressurePointEffect extends AbstractGameEffect {
    private float x;
    private float y;
    private float endX;

    public PressurePointEffect(float setX, float setY) {
        this.img = ImageMaster.DAGGER_STREAK;
        setX -= 120.0F * Settings.scale;
        setY -= -80.0F * Settings.scale;
        this.endX = setX - this.img.packedWidth / 2.0F;
        this.endY = setY - this.img.packedHeight / 2.0F;
        this.x = this.endX + MathUtils.random(-550.0F, -450.0F) * Settings.scale;
        this.y = this.endY + MathUtils.random(380.0F, 320.0F) * Settings.scale;
        this.startingDuration = 0.3F;
        this.duration = 0.3F;
        this.scaleMultiplier = MathUtils.random(0.05F, 0.2F);
        this.rotation = 150.0F;
        this.color = Color.VIOLET.cpy();
        this.color.a = 0.0F;
    }
    private float endY;
    private float scaleMultiplier;
    private TextureAtlas.AtlasRegion img;
    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        this.x = Interpolation.swingIn.apply(this.endX, this.x, this.duration * 3.33F);
        this.y = Interpolation.swingIn.apply(this.endY, this.y, this.duration * 3.33F);
        if (this.duration < 0.0F) {
            this.isDone = true;
            this.duration = 0.0F;
        }
        this.color.a = 1.0F - this.duration;
        this.scale = this.duration * Settings.scale + this.scaleMultiplier;
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale, this.scale * 1.5F, this.rotation);

        sb.setBlendFunction(770, 1);
        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale * 0.75F, this.scale * 0.75F, this.rotation);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * PressurePointEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

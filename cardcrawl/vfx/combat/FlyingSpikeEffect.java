package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class FlyingSpikeEffect extends AbstractGameEffect {
    private static final float DURATION = 0.75F;
    private float x;
    private float y;

    public FlyingSpikeEffect(float x, float y, float startingRotation, float vX, float vY, Color color) {
        this.img = ImageMaster.THICK_3D_LINE;
        this.duration = 0.75F;
        this.rotation = startingRotation;
        this.x = x - this.img.packedWidth / 2.0F;
        this.y = y - this.img.packedHeight / 2.0F;
        this.vX = vX;
        this.vY = vY;
        this.color = new Color(color.r, color.g, color.b, 0.0F);
        this.renderBehind = true;
        this.scale = 0.01F;
        this.rotation += MathUtils.random(-5.0F, 5.0F);
    }
    private float vX;
    private float vY;
    private TextureAtlas.AtlasRegion img;

    public void update() {
        this.scale = this.duration * 2.0F * Settings.scale;
        this.x += this.vX * Gdx.graphics.getDeltaTime();
        this.y += this.vY * Gdx.graphics.getDeltaTime();
        this.duration -= Gdx.graphics.getDeltaTime();

        if (this.duration > 0.5F) {
            this.color.a = (0.75F - this.duration) * 2.0F;
        } else {
            this.color.a = this.duration;
        }

        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setBlendFunction(770, 1);
        sb.setColor(this.color);
        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale, this.scale, this.rotation);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * FlyingSpikeEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

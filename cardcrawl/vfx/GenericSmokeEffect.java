package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class GenericSmokeEffect extends AbstractGameEffect {
    private float x;
    private float y;
    private float vY;

    public GenericSmokeEffect(float x, float y) {
        this.color = Color.WHITE.cpy();
        this.color.r = MathUtils.random(0.4F, 0.6F);
        this.color.g = this.color.r - 0.05F;
        this.color.b = this.color.r - 0.1F;
        this.color.a = 0.0F;
        this.renderBehind = false;

        if (MathUtils.randomBoolean()) {
            this.img = ImageMaster.EXHAUST_L;
            this.duration = MathUtils.random(0.9F, 1.2F);
            this.targetScale = MathUtils.random(0.9F, 1.3F);
        } else {
            this.img = ImageMaster.EXHAUST_S;
            this.duration = MathUtils.random(0.6F, 1.4F);
            this.targetScale = MathUtils.random(0.7F, 1.0F);
        }

        this.startDur = this.duration;

        this.x = x - this.img.packedWidth / 2.0F;
        this.y = y - this.img.packedHeight / 2.0F;
        this.scale = 0.01F;
        this.rotation = MathUtils.random(360.0F);
        this.aV = MathUtils.random(-250.0F, 250.0F);
        this.vY = MathUtils.random(1.0F * Settings.scale, 5.0F * Settings.scale);
    }
    private float aV;
    private TextureAtlas.AtlasRegion img;
    private float startDur;
    private float targetScale;
    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
        this.x += MathUtils.random(-2.0F * Settings.scale, 2.0F * Settings.scale);
        this.y += MathUtils.random(-2.0F * Settings.scale, 2.0F * Settings.scale);
        this.y += this.vY;
        this.rotation += this.aV * Gdx.graphics.getDeltaTime();
        this.scale = Interpolation.swing.apply(0.01F, this.targetScale, 1.0F - this.duration / this.startDur);

        if (this.duration < this.startDur / 2.0F) {
            this.color.a = this.duration * 3.0F;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale, this.scale, this.rotation);
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * GenericSmokeEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

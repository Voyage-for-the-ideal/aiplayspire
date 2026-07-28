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

public class CeilingDustCloudEffect extends AbstractGameEffect {
    private float x;
    private float y;
    private float vY;
    private float vX;

    public CeilingDustCloudEffect(float x, float y) {
        if (this.img == null) {
            this.img = ImageMaster.vfxAtlas.findRegion("env/dustCloud");
        }

        this.x = x + MathUtils.random(-40.0F, 40.0F) * Settings.scale - this.img.packedWidth / 2.0F;
        this.y = y - this.img.packedHeight / 2.0F;
        float randY = MathUtils.random(-10.0F, 10.0F) * Settings.scale;
        y += randY;
        this.renderBehind = (randY < 0.0F);

        this.vY = MathUtils.random(0.0F, 20.0F) * Settings.scale;
        this.vX = MathUtils.random(-30.0F, 30.0F) * Settings.scale;
        this.duration = MathUtils.random(3.0F, 7.0F);
        this.scale = Settings.scale * MathUtils.random(0.1F, 0.7F);
        this.rotation = MathUtils.random(0.0F, 360.0F);
        float c = MathUtils.random(0.1F, 0.3F);
        this.color = new Color(c + 0.1F, c, c, 0.0F);
        this.color.a = MathUtils.random(0.1F, 0.2F);
        this.startingAlpha = this.color.a;
        this.aV = MathUtils.random(-0.1F, 0.1F);
    }
    private float vYAccel;
    private float aV;
    private float startingAlpha;
    private TextureAtlas.AtlasRegion img;
    public void update() {
        this.rotation += this.aV;
        this.y -= this.vY * Gdx.graphics.getDeltaTime();
        this.x += this.vX * Gdx.graphics.getDeltaTime();
        this.vY += this.vYAccel * Gdx.graphics.getDeltaTime();
        this.vX *= 0.99F;
        this.scale += Gdx.graphics.getDeltaTime() * 0.2F;

        if (this.duration < 3.0F) {
            this.color.a = Interpolation.fade.apply(this.startingAlpha, 0.0F, 1.0F - this.duration / 3.0F);
        }

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
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
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\scene\
 * CeilingDustCloudEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

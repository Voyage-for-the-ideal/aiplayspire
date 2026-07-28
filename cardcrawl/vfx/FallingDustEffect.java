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

public class FallingDustEffect extends AbstractGameEffect {
    private float x;
    private float y;
    private float vY;
    private float vX;

    public FallingDustEffect(float x, float y) {
        this.x = x + MathUtils.random(-6.0F, 6.0F) * Settings.scale;
        this.y = y;
        float randY = MathUtils.random(-10.0F, 10.0F) * Settings.scale;
        y += randY;
        this.renderBehind = (randY < 0.0F);

        this.vY = MathUtils.random(0.0F, 140.0F) * Settings.scale;
        if (MathUtils.randomBoolean()) {
            this.vX = MathUtils.random(-20.0F, 20.0F) * Settings.scale;
        } else {
            this.vX = 0.0F;
        }
        this.vYAccel = MathUtils.random(4.0F, 9.0F) * Settings.scale;
        this.duration = MathUtils.random(3.0F, 7.0F);
        this.img = setImg();
        this.scale = Settings.scale * MathUtils.random(0.5F, 0.7F);
        this.rotation = MathUtils.random(0.0F, 360.0F);
        float c = MathUtils.random(0.1F, 0.3F);
        this.color = new Color(c + 0.1F, c, c, 0.0F);
        this.color.a = MathUtils.random(0.8F, 0.9F);
        this.startingAlpha = this.color.a;
        this.aV = MathUtils.random(-1.0F, 1.0F);
    }
    private float vYAccel;
    private float aV;
    private float startingAlpha;
    private TextureAtlas.AtlasRegion img;
    private TextureAtlas.AtlasRegion setImg() {
        switch (MathUtils.random(0, 5)) {
            case 0:
                return ImageMaster.DUST_1;
            case 1:
                return ImageMaster.DUST_2;
            case 2:
                return ImageMaster.DUST_3;
            case 3:
                return ImageMaster.DUST_4;
            case 4:
                return ImageMaster.DUST_5;
        }
        return ImageMaster.DUST_6;
    }

    public void update() {
        this.rotation += this.aV;
        this.y -= this.vY * Gdx.graphics.getDeltaTime();
        this.x += this.vX * Gdx.graphics.getDeltaTime();
        this.vY += this.vYAccel * Gdx.graphics.getDeltaTime();
        this.vX *= 0.99F;

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
        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.offsetX, this.img.offsetY, this.img.packedWidth,
                this.img.packedHeight, this.scale, this.scale, this.rotation);
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * FallingDustEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

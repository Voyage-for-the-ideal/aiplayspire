package com.megacrit.cardcrawl.vfx.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class DustEffect
        extends AbstractGameEffect {
    private TextureAtlas.AtlasRegion img = getImg();

    private float x = MathUtils.random(0, Settings.WIDTH);
    private float y = MathUtils.random(-100.0F, 400.0F) * Settings.scale + AbstractDungeon.floorY;
    private float vX = MathUtils.random(-12.0F, 12.0F) * Settings.scale;
    private float vY = MathUtils.random(-12.0F, 30.0F) * Settings.scale;
    private float aV;

    public DustEffect() {
        float colorTmp = MathUtils.random(0.1F, 0.7F);
        this.color = new Color(colorTmp, colorTmp, colorTmp, 0.0F);
        this.baseAlpha = 1.0F - colorTmp;
        this.color.a = 0.0F;

        this.rotation = MathUtils.random(0.0F, 360.0F);
        this.aV = MathUtils.random(-120.0F, 120.0F);
    }
    private float baseAlpha;
    private TextureAtlas.AtlasRegion getImg() {
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
        this.rotation += this.aV * Gdx.graphics.getDeltaTime();
        this.duration -= Gdx.graphics.getDeltaTime();
        this.x += this.vX * Gdx.graphics.getDeltaTime();
        this.y += this.vY * Gdx.graphics.getDeltaTime();

        if (this.duration < 0.0F) {
            this.isDone = true;
        }

        if (this.duration > this.startingDuration / 2.0F) {
            float tmp = this.duration - this.startingDuration / 2.0F;
            this.color.a = Interpolation.fade.apply(0.0F, 1.0F, this.startingDuration / 2.0F - tmp) * this.baseAlpha;
        } else {
            this.color.a = Interpolation.fade.apply(0.0F, 1.0F, this.duration / this.startingDuration / 2.0F)
                    * this.baseAlpha;
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
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\scene\
 * DustEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

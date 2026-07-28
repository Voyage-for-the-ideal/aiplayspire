package com.megacrit.cardcrawl.vfx.campfire;

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

public class CampfireBubbleEffect extends AbstractGameEffect {
    private float x;
    private boolean flipX = MathUtils.randomBoolean();
    private float y;
    private float aV;
    private boolean flipY = MathUtils.randomBoolean();
    private TextureAtlas.AtlasRegion img;

    public CampfireBubbleEffect(boolean isLarge) {
        this.duration = MathUtils.random(10.0F, 20.0F);
        this.startingDuration = this.duration;

        switch (MathUtils.random(2)) {
            case 0:
                this.img = ImageMaster.SMOKE_1;
                break;
            case 1:
                this.img = ImageMaster.SMOKE_2;
                break;
            default:
                this.img = ImageMaster.SMOKE_3;
                break;
        }

        this.x = MathUtils.random(-300.0F, 300.0F) * Settings.scale - this.img.packedWidth / 2.0F;
        if (isLarge) {
            this.y = MathUtils.random(-200.0F, 230.0F) * Settings.scale - this.img.packedHeight / 2.0F;
        } else {
            this.y = MathUtils.random(0.0F, 230.0F) * Settings.scale - this.img.packedHeight / 2.0F;
        }
        this.aV = MathUtils.random(-30.0F, 30.0F);

        this.rotation = MathUtils.random(0.0F, 360.0F);
        float tmp = MathUtils.random(0.8F, 1.0F);
        this.color = new Color();
        this.color.r = tmp;
        this.color.g = tmp - 0.03F;
        this.color.b = tmp - 0.07F;
        this.scale = MathUtils.random(6.0F, 9.0F) * Settings.scale;
    }

    public void update() {
        this.rotation += this.aV * Gdx.graphics.getDeltaTime();

        if (this.startingDuration - this.duration < 3.0F) {
            this.color.a = Interpolation.fade.apply(0.0F, 0.5F, (this.startingDuration - this.duration) / 3.0F);
        } else if (this.duration < 3.0F) {
            this.color.a = Interpolation.fade.apply(0.5F, 0.0F, 1.0F - this.duration / 3.0F);
        }
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb, float srcX, float srcY) {
        sb.setColor(this.color);
        if (this.flipX && !this.img.isFlipX()) {
            this.img.flip(true, false);
        } else if (!this.flipX && this.img.isFlipX()) {
            this.img.flip(true, false);
        }
        if (this.flipY && !this.img.isFlipY()) {
            this.img.flip(false, true);
        } else if (!this.flipY && this.img.isFlipY()) {
            this.img.flip(false, true);
        }
        sb.draw((TextureRegion) this.img, srcX + this.x, srcY + this.y, this.img.packedWidth / 2.0F,
                this.img.packedHeight / 2.0F, this.img.packedWidth, this.img.packedHeight, this.scale, this.scale,
                this.rotation);
    }

    public void dispose() {
    }

    public void render(SpriteBatch sb) {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\campfire\
 * CampfireBubbleEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

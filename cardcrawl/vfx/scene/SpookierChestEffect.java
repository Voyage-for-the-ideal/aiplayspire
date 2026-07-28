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
import com.megacrit.cardcrawl.rewards.chests.AbstractChest;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class SpookierChestEffect extends AbstractGameEffect {
    private float x;
    private float y;
    private boolean flipX = MathUtils.randomBoolean();
    private float vX;
    private float vY;
    private float aV;
    private boolean flipY = MathUtils.randomBoolean();
    private TextureAtlas.AtlasRegion img;

    public SpookierChestEffect() {
        this.duration = 3.0F;
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

        this.x = AbstractChest.CHEST_LOC_X + MathUtils.random(-30.0F, 30.0F) * Settings.scale
                - this.img.packedWidth / 2.0F;
        this.y = AbstractChest.CHEST_LOC_Y - MathUtils.random(120.0F, 190.0F) * Settings.scale
                - this.img.packedHeight / 2.0F;
        this.vX = MathUtils.random(-120.0F, 120.0F) * Settings.scale;
        this.vY = MathUtils.random(-50.0F, 100.0F) * Settings.scale;
        this.aV = MathUtils.random(-150.0F, 150.0F);

        float tmp = MathUtils.random(0.3F, 0.9F);
        this.color = new Color();
        this.color.r = tmp * MathUtils.random(0.7F, 1.0F);
        this.color.g = tmp * MathUtils.random(0.5F, 0.9F);
        this.color.b = tmp;
        this.renderBehind = true;
        this.scale = MathUtils.random(0.5F, 3.0F) * Settings.scale;
    }

    public void update() {
        this.x += this.vX * Gdx.graphics.getDeltaTime();
        this.y += this.vY * Gdx.graphics.getDeltaTime();
        this.rotation += this.aV * Gdx.graphics.getDeltaTime();

        if (this.startingDuration - this.duration < 1.0F) {
            this.color.a = Interpolation.fade.apply(0.0F, 0.3F, (this.startingDuration - this.duration) / 1.0F);
        } else if (this.duration < 2.0F) {
            this.color.a = Interpolation.fade.apply(0.3F, 0.0F, 1.0F - this.duration / 2.0F);
        }
        this.duration -= Gdx.graphics.getDeltaTime();
        this.scale += Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
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

        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale, this.scale, this.rotation);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\scene\
 * SpookierChestEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

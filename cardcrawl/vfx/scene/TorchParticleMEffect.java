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

public class TorchParticleMEffect
        extends AbstractGameEffect {
    private float x;
    private float y;
    private float vY;
    private TextureAtlas.AtlasRegion img;
    public static boolean renderGreen = false;

    public TorchParticleMEffect(float x, float y) {
        this.duration = MathUtils.random(1.5F, 3.0F);
        this.startingDuration = this.duration;
        this.img = getImg();
        this.x = x - (this.img.packedWidth / 2) + MathUtils.random(-3.0F, 3.0F) * Settings.scale;
        this.y = y - (this.img.packedHeight / 2);
        this.scale = Settings.scale * MathUtils.random(1.0F, 2.0F);
        this.vY = MathUtils.random(1.0F, 10.0F);
        this.vY *= this.vY * Settings.scale;
        this.rotation = MathUtils.random(-20.0F, 20.0F);

        if (!renderGreen) {
            this

                    .color = new Color(MathUtils.random(0.6F, 1.0F), MathUtils.random(0.3F, 0.6F),
                            MathUtils.random(0.0F, 0.3F), 0.01F);
        } else {

            this

                    .color = new Color(MathUtils.random(0.1F, 0.3F), MathUtils.random(0.5F, 0.9F),
                            MathUtils.random(0.1F, 0.3F), 0.01F);
        }

        this.renderBehind = true;
    }

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
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
        this.color.a = Interpolation.fade.apply(0.0F, 0.75F, this.duration / this.startingDuration);
        this.y += this.vY * Gdx.graphics.getDeltaTime();
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
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\scene\
 * TorchParticleMEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

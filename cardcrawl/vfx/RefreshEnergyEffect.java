package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class RefreshEnergyEffect
        extends AbstractGameEffect {
    private static final float EFFECT_DUR = 0.4F;
    private float scale = Settings.scale / 1.2F;
    private Color color = new Color(1.0F, 1.0F, 1.0F, 1.0F);

    private TextureAtlas.AtlasRegion img;

    public RefreshEnergyEffect() {
        this.img = ImageMaster.WHITE_RING;
        this.x = 198.0F * Settings.scale - this.img.packedWidth / 2.0F;
        this.y = 190.0F * Settings.scale - this.img.packedHeight / 2.0F;
        this.duration = 0.4F;
    }
    private float x;
    private float y;

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        this.scale *= 1.0F + Gdx.graphics.getDeltaTime() * 2.5F;
        this.color.a = Interpolation.fade.apply(0.0F, 0.75F, this.duration / 0.4F);
        if (this.color.a < 0.0F) {
            this.color.a = 0.0F;
        }

        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.setBlendFunction(770, 1);
        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale * 1.5F, this.scale * 1.5F, this.rotation);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * RefreshEnergyEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

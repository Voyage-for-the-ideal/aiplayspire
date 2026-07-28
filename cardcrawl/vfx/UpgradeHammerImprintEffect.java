package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class UpgradeHammerImprintEffect extends AbstractGameEffect {
    private TextureAtlas.AtlasRegion img = ImageMaster.UPGRADE_HAMMER_IMPACT;

    private static final float DUR = 0.7F;
    private float x;
    private Color shineColor = new Color(1.0F, 1.0F, 1.0F, 0.0F);
    private float y;
    private float hammerGlowScale;

    public UpgradeHammerImprintEffect(float x, float y) {
        this.x = x - (this.img.packedWidth / 2);
        this.y = y - (this.img.packedHeight / 2);
        this.color = Color.WHITE.cpy();
        this.color.a = 0.7F;
        this.duration = 0.7F;
        this.scale = Settings.scale / MathUtils.random(1.0F, 1.5F);
        this.rotation = MathUtils.random(0.0F, 360.0F);
        this.hammerGlowScale = 1.0F - this.duration;
        this.hammerGlowScale *= this.hammerGlowScale;
    }

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
        this.color.a = this.duration;

        this.hammerGlowScale = 1.7F - this.duration;
        this.hammerGlowScale *= this.hammerGlowScale * this.hammerGlowScale;
        this.scale += Gdx.graphics.getDeltaTime() / 20.0F;
    }

    public void render(SpriteBatch sb) {
        sb.setBlendFunction(770, 1);
        sb.setColor(this.color);
        sb.draw((TextureRegion) this.img, this.x +

                MathUtils.random(-2.0F, 2.0F) * Settings.scale,
                this.y +
                        MathUtils.random(-2.0F, 2.0F) * Settings.scale,
                this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F, this.img.packedWidth, this.img.packedHeight,
                this.scale, this.scale, this.rotation);

        this.color.a /= 10.0F;
        sb.setColor(this.shineColor);
        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.hammerGlowScale, this.hammerGlowScale, this.rotation);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * UpgradeHammerImprintEffect.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */

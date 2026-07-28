package com.megacrit.cardcrawl.vfx.combat;

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

public class FrostOrbPassiveEffect extends AbstractGameEffect {
    private float effectDuration;
    private float x;
    private TextureAtlas.AtlasRegion img = ImageMaster.ROOM_SHINE_1;
    private float y;
    private float vY;
    private float alpha;

    public FrostOrbPassiveEffect(float x, float y) {
        this.effectDuration = MathUtils.random(0.4F, 0.8F);
        this.duration = this.effectDuration;
        this.startingDuration = this.effectDuration;
        float offset = MathUtils.random(-34.0F, 34.0F) * Settings.scale;
        this.x = x + offset - this.img.packedWidth / 2.0F;
        if (offset > 0.0F) {
            this.renderBehind = true;
        }
        this.y = y + MathUtils.random(-28.0F, 20.0F) * Settings.scale - this.img.packedWidth / 2.0F;
        this.vY = MathUtils.random(2.0F, 20.0F) * Settings.scale;
        this.alpha = MathUtils.random(0.5F, 1.0F);

        this.color = new Color(MathUtils.random(0.6F, 0.9F), 1.0F, 1.0F, this.alpha);
        this.scale = MathUtils.random(0.5F, 1.2F) * Settings.scale;
    }

    public void update() {
        if (this.vY != 0.0F) {
            this.y += this.vY * Gdx.graphics.getDeltaTime();
            MathUtils.lerp(this.vY, 0.0F, Gdx.graphics.getDeltaTime() * 10.0F);
            if (this.vY < 0.5F) {
                this.vY = 0.0F;
            }
        }

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        } else if (this.duration < this.effectDuration / 2.0F) {
            this.color.a = Interpolation.exp5In.apply(0.0F, this.alpha, this.duration / this.effectDuration / 2.0F);
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.setBlendFunction(770, 1);
        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale *

                        MathUtils.random(0.75F, 1.25F),
                this.scale *
                        MathUtils.random(0.75F, 1.25F),
                this.rotation);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * FrostOrbPassiveEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

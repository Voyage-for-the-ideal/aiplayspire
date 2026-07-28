package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class EmptyStanceParticleEffect
        extends AbstractGameEffect {
    private float rotationSpeed;
    private Vector2 pos;

    public EmptyStanceParticleEffect(float x, float y) {
        if (img == null) {
            img = ImageMaster.STRIKE_BLUR;
        }

        this.startingDuration = 0.6F;
        this.duration = this.startingDuration;

        this.pos = new Vector2(x, y);

        this.rotationSpeed = MathUtils.random(120.0F, 150.0F);
        this.rotation = MathUtils.random(360.0F);
        this.scale = MathUtils.random(0.7F, 2.5F) * Settings.scale;

        this.color = new Color(MathUtils.random(0.2F, 0.4F), MathUtils.random(0.6F, 0.8F), 1.0F, 0.0F);
        this.renderBehind = MathUtils.randomBoolean(0.8F);
    }
    private Vector2 pos2;
    private Vector2 pos3;
    private static TextureAtlas.AtlasRegion img;

    public void update() {
        this.pos2 = new Vector2(MathUtils.cosDeg(this.rotation), MathUtils.sinDeg(this.rotation));
        this.pos2.nor();
        this.pos2.x *= 10.0F;
        this.pos2.y *= 10.0F;
        this.pos3 = this.pos.sub(this.pos2);

        this.rotation += Gdx.graphics.getDeltaTime() * this.rotationSpeed;
        this.duration -= Gdx.graphics.getDeltaTime();

        if (this.duration > this.startingDuration / 2.0F) {
            this.color.a = Interpolation.fade.apply(1.0F, 0.0F, (this.duration - this.startingDuration / 2.0F) * 2.0F);
        } else {
            this.color.a = Interpolation.fade.apply(0.0F, 1.0F, this.duration * 2.0F);
        }

        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        if (this.pos3 != null) {
            sb.setBlendFunction(770, 1);
            sb.setColor(this.color);
            sb.draw((TextureRegion) img, this.pos3.x - img.packedWidth / 2.0F, this.pos3.y - img.packedHeight / 2.0F,
                    img.packedWidth / 2.0F, img.packedHeight / 2.0F, img.packedWidth, img.packedHeight, this.scale +

                            MathUtils.random(-0.08F, 0.08F),
                    this.scale +
                            MathUtils.random(-0.08F, 0.08F),
                    this.rotation);

            sb.setBlendFunction(770, 771);
        }
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * EmptyStanceParticleEffect.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */

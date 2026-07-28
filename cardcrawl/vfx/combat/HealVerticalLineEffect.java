package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class HealVerticalLineEffect extends AbstractGameEffect {
    private float x;
    private float y;
    private float vY;

    public HealVerticalLineEffect(float x, float y) {
        this.img = ImageMaster.STRIKE_LINE;
        this.img2 = ImageMaster.STRIKE_LINE_2;
        this.duration = MathUtils.random(0.6F, 1.3F);
        this.startingDuration = this.duration;
        this.x = x;
        this.y = y;
        this.staggerTimer = MathUtils.random(0.0F, 0.5F);
        float tmp = MathUtils.random(5.0F, 20.0F);
        this.vY = tmp * tmp * Settings.scale;
        this.rotation = 90.0F;

        if (MathUtils.randomBoolean()) {
            this.color = Color.CHARTREUSE.cpy();
        } else {
            this.color = new Color(1.0F, 1.0F, 0.5F, 1.0F);
        }
        this.color.a = 0.0F;
        this.renderBehind = MathUtils.randomBoolean(0.3F);
    }
    private float staggerTimer;
    private TextureAtlas.AtlasRegion img;
    private TextureAtlas.AtlasRegion img2;
    public void update() {
        if (this.staggerTimer > 0.0F) {
            this.staggerTimer -= Gdx.graphics.getDeltaTime();

            return;
        }
        this.y += this.vY * Gdx.graphics.getDeltaTime();

        this.scale = Settings.scale * this.duration / this.startingDuration;
        this.duration -= Gdx.graphics.getDeltaTime();

        if (this.duration / this.startingDuration > 0.5F) {
            this.color.a = 1.0F - this.duration / this.startingDuration;
            this.color.a += MathUtils.random(0.0F, 0.2F);
        } else {
            this.color.a = this.duration / this.startingDuration;
            this.color.a += MathUtils.random(0.0F, 0.2F);
        }
        if (this.duration < 0.0F) {
            this.isDone = true;
            this.color.a = 0.0F;
        }
    }

    public void render(SpriteBatch sb) {
        if (this.staggerTimer > 0.0F) {
            return;
        }
        sb.setBlendFunction(770, 1);
        sb.setColor(this.color);

        sb.draw((TextureRegion) this.img, this.x - this.img.packedWidth / 2.0F, this.y - this.img.packedHeight / 2.0F,
                this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F, this.img.packedWidth, this.img.packedHeight,
                this.scale *

                        MathUtils.random(0.7F, 2.0F),
                this.scale * 0.8F, this.rotation +

                        MathUtils.random(-2.0F, 2.0F));
        sb.setColor(new Color(1.0F, 1.0F, 0.7F, this.color.a));
        sb.draw((TextureRegion) this.img2, this.x - this.img2.packedWidth / 2.0F,
                this.y - this.img2.packedHeight / 2.0F, this.img2.packedWidth / 2.0F, this.img2.packedHeight / 2.0F,
                this.img2.packedWidth, this.img2.packedHeight, this.scale * 1.5F, this.scale *

                        MathUtils.random(1.2F, 2.4F),
                this.rotation);

        sb.draw((TextureRegion) this.img2, this.x - this.img2.packedWidth / 2.0F,
                this.y - this.img2.packedHeight / 2.0F, this.img2.packedWidth / 2.0F, this.img2.packedHeight / 2.0F,
                this.img2.packedWidth, this.img2.packedHeight, this.scale * 1.5F, this.scale *

                        MathUtils.random(1.2F, 2.4F),
                this.rotation);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * HealVerticalLineEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

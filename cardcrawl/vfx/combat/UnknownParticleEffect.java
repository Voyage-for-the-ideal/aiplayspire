package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class UnknownParticleEffect extends AbstractGameEffect {
    private Texture img;
    private static final int RAW_W = 128;
    private static final float DURATION = 1.5F;
    private static int renderNum = 0;
    private float x;
    private float y;
    private float scale;
    private float targetScale;

    public UnknownParticleEffect(float x, float y) {
        if (renderNum == 0) {
            this.targetScale = Settings.scale * 0.8F;
            this.rotation = 24.0F;
            this.x = x - 24.0F * Settings.scale;
            this.y = y - MathUtils.random(6.0F, 10.0F) * Settings.scale;
            if (MathUtils.randomBoolean()) {
                this.color = Color.GOLDENROD.cpy();
            } else {
                this.color = Color.GOLD.cpy();
            }
            this.renderBehind = true;
        } else if (renderNum == 1) {
            this.targetScale = Settings.scale * 1.2F;
            this.rotation = 0.0F;
            this.x = x;
            this.y = y;
            this.color = Color.WHITE.cpy();
            this.renderBehind = false;
        } else {
            this.targetScale = Settings.scale * 0.8F;
            this.rotation = -24.0F;
            this.x = x + 24.0F * Settings.scale;
            this.y = y - MathUtils.random(6.0F, 10.0F) * Settings.scale;
            if (MathUtils.randomBoolean()) {
                this.color = Color.GOLDENROD.cpy();
            } else {
                this.color = Color.GOLD.cpy();
            }
            this.renderBehind = true;
        }
        this.scale = 0.01F;

        renderNum++;
        if (renderNum > 2) {
            renderNum = 0;
        }

        this.img = ImageMaster.INTENT_UNKNOWN_L;
    }

    public void update() {
        if (this.duration > 0.5F) {
            this.scale = Interpolation.elasticOut.apply(0.01F, this.targetScale, 1.5F - this.duration);
        }

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        } else if (this.duration < 0.5F) {
            this.color.a = this.duration * 2.0F;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.draw(this.img, this.x - 64.0F, this.y - 64.0F, 64.0F, 64.0F, 128.0F, 128.0F, this.scale, this.scale,
                this.rotation, 0, 0, 128, 128, false, false);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * UnknownParticleEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class ShieldParticleEffect extends AbstractGameEffect {
    private static final int RAW_W = 64;
    private float x;
    private float y;
    private float scale = Settings.scale / 2.0F;

    public ShieldParticleEffect(float x, float y) {
        this.duration = 2.0F;

        this.x = x;
        this.y = y;
        this.renderBehind = true;
        this.color = new Color(1.0F, 1.0F, 1.0F, 0.0F);
    }

    public void update() {
        this.scale += Gdx.graphics.getDeltaTime() * Settings.scale * 1.1F;

        if (this.duration > 1.0F) {
            this.color.a = Interpolation.fade.apply(0.0F, 0.3F, 1.0F - this.duration - 1.0F);
        } else {
            this.color.a = Interpolation.fade.apply(0.3F, 0.0F, 1.0F - this.duration);
        }

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setBlendFunction(770, 1);
        sb.setColor(this.color);
        sb.draw(ImageMaster.INTENT_DEFEND, this.x - 32.0F, this.y - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, this.scale,
                this.scale, 0.0F, 0, 0, 64, 64, false, false);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * ShieldParticleEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

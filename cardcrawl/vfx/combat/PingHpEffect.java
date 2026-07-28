package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class PingHpEffect
        extends AbstractGameEffect {
    private float x;

    public PingHpEffect(float x) {
        this.duration = 1.5F;
        this.color = new Color(1.0F, 1.0F, 0.2F, 0.0F);
        this.x = x;
    }

    public void update() {
        this.scale = Interpolation.pow5In.apply(1.15F, 1.0F, this.duration / 1.5F);
        this.color.a = Interpolation.pow2Out.apply(0.0F, 0.7F, this.duration / 1.5F);
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.setBlendFunction(770, 1);
        sb.draw(ImageMaster.TP_HP, this.x - 32.0F + 32.0F * Settings.scale,
                Settings.HEIGHT - 32.0F * Settings.scale - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F,
                this.scale * Settings.scale, this.scale * Settings.scale, this.rotation, 0, 0, 64, 64, false, false);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * PingHpEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

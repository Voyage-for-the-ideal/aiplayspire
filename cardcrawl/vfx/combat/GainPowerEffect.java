package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class GainPowerEffect
        extends AbstractGameEffect {
    private static final float EFFECT_DUR = 2.0F;
    private float scale;
    private TextureAtlas.AtlasRegion region48;

    public GainPowerEffect(AbstractPower power) {
        if (power.img == null) {
            this.region48 = power.region48;
        }
        this.duration = 2.0F;
        this.startingDuration = 2.0F;

        power.playApplyPowerSfx();
        this.scale = Settings.scale;
        this.color = new Color(1.0F, 1.0F, 1.0F, 0.5F);
    }

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();

        if (this.duration > 0.5F) {
            this.scale = Interpolation.exp5Out.apply(3.0F * Settings.scale, Settings.scale,
                    -(this.duration - 2.0F) / 1.5F);
        } else {
            this.color.a = Interpolation.fade.apply(0.5F, 0.0F, 1.0F - this.duration);
        }
    }

    public void render(SpriteBatch sb, float x, float y) {
        sb.setColor(this.color);
        sb.setBlendFunction(770, 1);
        if (this.region48 != null) {
            sb.draw((TextureRegion) this.region48, x - this.region48.packedWidth / 2.0F,
                    y - this.region48.packedHeight / 2.0F, this.region48.packedWidth / 2.0F,
                    this.region48.packedHeight / 2.0F, this.region48.packedWidth, this.region48.packedHeight,
                    this.scale, this.scale, 0.0F);
        }

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }

    public void render(SpriteBatch sb) {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * GainPowerEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

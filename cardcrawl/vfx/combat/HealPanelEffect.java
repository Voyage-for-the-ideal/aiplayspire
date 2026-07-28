package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class HealPanelEffect
        extends AbstractGameEffect {
    private float x;
    private static Texture img = null;

    public HealPanelEffect(float x) {
        this.x = x;
        if (img == null) {
            img = ImageMaster.loadImage("images/ui/topPanel/panel_heart_white.png");
        }
        this.color = Color.CHARTREUSE.cpy();
        this.color.a = 0.0F;
        this.duration = 1.5F;
        this.scale = Settings.scale;
    }

    public void update() {
        this.scale = Interpolation.exp10In.apply(1.2F, 2.0F, this.duration / 1.5F) * Settings.scale;

        if (this.duration > 1.0F) {
            this.color.a = Interpolation.pow5In.apply(0.6F, 0.0F, (this.duration - 1.0F) * 2.0F);
        } else {
            this.color.a = Interpolation.fade.apply(0.0F, 0.6F, this.duration);
        }

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.setBlendFunction(770, 1);
        sb.draw(img, this.x - 32.0F + 32.0F * Settings.scale, Settings.HEIGHT - 32.0F * Settings.scale - 32.0F, 32.0F,
                32.0F, 64.0F, 64.0F, this.scale, this.scale, this.rotation, 0, 0, 64, 64, false, false);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * HealPanelEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

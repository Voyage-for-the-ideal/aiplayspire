package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class EntangleEffect extends AbstractGameEffect {
    float x;
    float y;
    float dX;

    public EntangleEffect(float x, float y, float dX, float dY) {
        this.tX = x - 32.0F;
        this.tY = y - 32.0F;
        this.dX = dX - 32.0F;
        this.dY = dY - 32.0F;
        this.x = dX;
        this.y = dY;
        this.startingDuration = 1.0F;
        this.duration = this.startingDuration;

        this.scale = 0.01F;
        this.color = new Color(1.0F, 1.0F, 1.0F, 0.0F);
        this.renderBehind = false;
    }
    float dY;
    float tX;
    float tY;
    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        this.x = Interpolation.pow5In.apply(this.dX, this.tX, this.duration);
        this.y = Interpolation.pow5In.apply(this.dY, this.tY, this.duration);

        if (this.duration > this.startingDuration / 2.0F) {
            this.color.a = Interpolation.fade.apply(1.0F, 0.01F, this.duration - this.startingDuration / 2.0F)
                    * Settings.scale;
        } else {
            this.color.a = Interpolation.fade.apply(0.01F, 1.0F, this.duration / this.startingDuration / 2.0F)
                    * Settings.scale;
        }

        this.scale = Interpolation.bounceIn.apply(5.0F, 1.0F, this.duration) * Settings.scale;

        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(new Color(1.0F, 1.0F, 1.0F, this.color.a));
        sb.setBlendFunction(770, 1);
        sb.draw(ImageMaster.WEB_VFX, this.x, this.y, 32.0F, 32.0F, 64.0F, 64.0F, this.scale, this.scale, 0.0F, 0, 0, 64,
                64, false, false);
        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * EntangleEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class EndTurnGlowEffect
        extends AbstractGameEffect {
    private static final float DURATION = 2.0F;
    private float scale = 0.0F;
    private static final int IMG_W = 256;

    public EndTurnGlowEffect() {
        this.duration = 2.0F;
        this.color = Color.WHITE.cpy();
    }

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        this.scale = Interpolation.fade.apply(Settings.scale, 2.0F * Settings.scale, 1.0F - this.duration / 2.0F);
        this.color.a = Interpolation.fade.apply(0.4F, 0.0F, 1.0F - this.duration / 2.0F) / 2.0F;

        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb, float x, float y) {
        sb.setBlendFunction(770, 1);
        sb.setColor(this.color);
        sb.draw(ImageMaster.END_TURN_BUTTON_GLOW, x - 128.0F, y - 128.0F, 128.0F, 128.0F, 256.0F, 256.0F, this.scale,
                this.scale, 0.0F, 0, 0, 256, 256, false, false);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }

    public void render(SpriteBatch sb) {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * EndTurnGlowEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

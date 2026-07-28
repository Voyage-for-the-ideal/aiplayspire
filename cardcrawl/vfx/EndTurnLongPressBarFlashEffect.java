package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class EndTurnLongPressBarFlashEffect
        extends AbstractGameEffect {
    private Color bgColor = new Color(0.0F, 0.0F, 0.0F, 0.0F);

    public EndTurnLongPressBarFlashEffect() {
        this.duration = 1.0F;
        this.color = new Color(1.0F, 1.0F, 0.6F, 0.0F);
    }

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
        this.color.a = Interpolation.exp5Out.apply(0.0F, 1.0F, this.duration);
    }

    public void render(SpriteBatch sb) {
        this.color.a *= 0.25F;
        sb.setColor(this.bgColor);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 1533.0F * Settings.xScale, 256.0F * Settings.scale,
                214.0F * Settings.scale, 20.0F * Settings.scale);

        sb.setBlendFunction(770, 1);
        this.color.r = 1.0F;
        this.color.g = 1.0F;
        this.color.b = 0.6F;
        sb.setColor(this.color);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 1540.0F * Settings.xScale, 263.0F * Settings.scale,
                200.0F * Settings.scale, 6.0F * Settings.scale);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * EndTurnLongPressBarFlashEffect.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */

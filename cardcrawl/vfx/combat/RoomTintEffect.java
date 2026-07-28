package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class RoomTintEffect extends AbstractGameEffect {
    private static final float DEFAULT_DUR = 2.0F;
    private float tintTransparency;

    public RoomTintEffect(Color color, float tintTransparency) {
        this(color, tintTransparency, 2.0F, true);
    }

    public RoomTintEffect(Color color, float tintTransparency, float setDuration, boolean renderBehind) {
        this.renderBehind = renderBehind;
        this.startingDuration = setDuration;
        this.duration = setDuration;
        this.color = color;
        this.color.a = 0.0F;
        this.tintTransparency = tintTransparency;
    }

    public void update() {
        if (this.duration > this.startingDuration * 0.5F) {
            this.color.a = Interpolation.fade.apply(this.tintTransparency, 0.0F,
                    (this.duration - this.startingDuration * 0.5F) / this.startingDuration);

        } else if (this.duration < this.startingDuration * 0.5F) {
            this.color.a = Interpolation.fade.apply(0.0F, this.tintTransparency,
                    this.duration / this.startingDuration / 0.5F);
        }

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0.0F, 0.0F, Settings.WIDTH, Settings.HEIGHT);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * RoomTintEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

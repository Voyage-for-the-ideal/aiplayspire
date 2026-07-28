package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;

public class GlowRelicParticle
        extends AbstractGameEffect {
    private static final float DURATION = 3.0F;
    private float scale = 0.01F;

    private static final int IMG_W = 128;
    private Texture img;

    public GlowRelicParticle(Texture img, float x, float y, float angle) {
        this.duration = 3.0F;
        this.img = img;
        this.x = x;
        this.y = y;
        this.rotation = angle;
        this.color = Color.WHITE.cpy();
    }
    private float x;
    private float y;

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        this.scale = Interpolation.fade.apply(Settings.scale, 2.0F * Settings.scale, 1.0F - this.duration / 3.0F);
        this.color.a = Interpolation.fade.apply(1.0F, 0.0F, 1.0F - this.duration / 3.0F) / 2.0F;

        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setBlendFunction(770, 1);
        sb.setColor(this.color);
        sb.draw(this.img, this.x - 64.0F, this.y - 64.0F, 64.0F, 64.0F, 128.0F, 128.0F, this.scale, this.scale,
                this.rotation, 0, 0, 128, 128, false, false);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * GlowRelicParticle.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

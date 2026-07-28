package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class DebuffParticleEffect extends AbstractGameEffect {
    private Texture img;
    private static int IMG_NUM = 0;

    private boolean spinClockwise;
    private float scale = 0.0F;
    private float x;
    private float y;

    public DebuffParticleEffect(float x, float y) {
        this.x = x + MathUtils.random(-36.0F, 36.0F) * Settings.scale;
        this.y = y + MathUtils.random(-36.0F, 36.0F) * Settings.scale;
        this.duration = 4.0F;
        this.rotation = MathUtils.random(360.0F);
        this.spinClockwise = MathUtils.randomBoolean();

        if (IMG_NUM == 0) {
            this.renderBehind = true;
            this.img = ImageMaster.DEBUFF_VFX_1;
            IMG_NUM++;
        } else if (IMG_NUM == 1) {
            this.img = ImageMaster.DEBUFF_VFX_3;
            IMG_NUM++;
        } else {
            this.img = ImageMaster.DEBUFF_VFX_2;
            IMG_NUM = 0;
        }

        this.color = new Color(1.0F, 1.0F, 1.0F, 0.0F);
    }

    public void update() {
        if (this.spinClockwise) {
            this.rotation += Gdx.graphics.getDeltaTime() * 120.0F;
        } else {
            this.rotation -= Gdx.graphics.getDeltaTime() * 120.0F;
        }

        if (this.duration > 3.0F) {
            this.color.a = Interpolation.fade.apply(0.0F, 1.0F, 1.0F - this.duration - 3.0F);
        } else if (this.duration <= 1.0F) {

            this.color.a = Interpolation.fade.apply(1.0F, 0.0F, 1.0F - this.duration);
            this.scale = Interpolation.fade.apply(Settings.scale, 0.0F, 1.0F - this.duration);
        }

        if (this.duration > 2.0F) {
            this.scale = Interpolation.bounceOut.apply(0.0F, Settings.scale, 2.0F - this.duration - 2.0F);
        }

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.draw(this.img, this.x - 16.0F, this.y - 16.0F, 16.0F, 16.0F, 32.0F, 32.0F, this.scale, this.scale,
                this.rotation, 0, 0, 32, 32, false, false);
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * DebuffParticleEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

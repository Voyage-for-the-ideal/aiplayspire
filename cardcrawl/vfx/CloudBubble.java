package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class CloudBubble {
    private static final int RAW_W = 128;
    private float x;
    private float scale = 0.01F;
    private float y;
    private float fadeInTime;
    private float fadeInTimer;
    private float targetScale;
    private boolean fadingIn = true;
    private FloatyEffect f_effect;
    private Color color;
    private Texture img;
    private boolean killed = false;
    private float killSpeed;

    public CloudBubble(float x, float y, float targetScale) {
        this.x = x;
        this.y = y;
        this.targetScale = targetScale * Settings.scale;
        this.fadeInTime = MathUtils.random(0.7F, 2.5F);
        this.fadeInTimer = this.fadeInTime;
        this.f_effect = new FloatyEffect(this.targetScale * 3.0F, 1.0F);
        float darkness = MathUtils.random(0.8F, 0.9F);
        this.color = new Color(darkness, darkness - 0.04F, darkness - 0.05F, 1.0F);

        if (targetScale > 0.5F) {
            this.img = ImageMaster.LARGE_CLOUD;
        } else {
            this.img = ImageMaster.SMALL_CLOUD;
            this.targetScale *= 3.0F;
        }
        this.killSpeed = MathUtils.random(8.0F, 24.0F);
    }

    public void update() {
        this.f_effect.update();
        if (this.fadingIn) {
            this.fadeInTimer -= Gdx.graphics.getDeltaTime();
            if (this.fadeInTimer < 0.0F) {
                this.fadeInTimer = 0.0F;
                this.fadingIn = false;
            }
            this.scale = Interpolation.swingIn.apply(this.targetScale, 0.0F, this.fadeInTimer / this.fadeInTime);
        }

        if (this.killed) {
            this.color.a = MathUtils.lerp(this.color.a, 0.0F, Gdx.graphics.getDeltaTime() * this.killSpeed);
        }
    }

    public void kill() {
        this.killed = true;
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.draw(this.img, this.x - 64.0F + this.f_effect.x, this.y - 64.0F + this.f_effect.y, 64.0F, 64.0F, 128.0F,
                128.0F, this.scale, this.scale, 0.0F, 0, 0, 128, 128, false, false);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\CloudBubble.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

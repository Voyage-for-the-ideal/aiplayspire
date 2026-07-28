package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class FastSmoke {
    private float x;
    private float y;
    private float vX;
    private float scale = 0.01F;
    private float vY;
    private float rotation;
    private float fadeInTime;
    private float fadeInTimer;
    private float targetScale;
    private boolean fadingIn = true;
    private Color color;
    private TextureAtlas.AtlasRegion img;
    private boolean killed = false;
    private float killSpeed;
    private float decelerateY;

    public FastSmoke(float x, float y) {
        this.targetScale = MathUtils.random(1.0F, 1.5F) * Settings.scale;
        this.fadeInTime = MathUtils.random(1.0F, 1.5F);
        this.fadeInTimer = this.fadeInTime;
        float darkness = MathUtils.random(0.4F, 0.9F);
        this.color = new Color(darkness + 0.1F, darkness + 0.1F, darkness + 0.05F, 1.0F);

        if (this.targetScale > 0.5F) {
            this.img = ImageMaster.EXHAUST_L;
        } else {
            this.img = ImageMaster.EXHAUST_S;
            this.vX /= 3.0F;
        }

        this.x = x + MathUtils.random(-75.0F, 75.0F) * Settings.scale - this.img.packedWidth / 2.0F;
        this.y = y + MathUtils.random(-75.0F, 75.0F) * Settings.scale - this.img.packedHeight / 2.0F;
        this.vY = MathUtils.random(50.0F * Settings.scale, 400.0F * Settings.scale);
        this.vX = MathUtils.random(-140.0F * Settings.scale, 140.0F * Settings.scale);
        this.rotation = MathUtils.random(360.0F);
        this.killSpeed = MathUtils.random(1.0F, 4.0F);
        this.decelerateY = MathUtils.random(1.0F * Settings.scale, 3.0F * Settings.scale);
    }

    public void update() {
        if (this.fadingIn) {
            this.fadeInTimer -= Gdx.graphics.getDeltaTime();
            if (this.fadeInTimer < 0.0F) {
                this.fadeInTimer = 0.0F;
                this.fadingIn = false;
            }
            this.scale = Interpolation.swingIn.apply(this.targetScale, 0.01F, this.fadeInTimer / this.fadeInTime);
        }

        this.y += this.vY * Gdx.graphics.getDeltaTime();
        this.vY -= Gdx.graphics.getDeltaTime() * this.decelerateY;
        this.x += this.vX * Gdx.graphics.getDeltaTime();
        this.rotation += this.vX * 2.0F * Gdx.graphics.getDeltaTime();

        if (this.vY < 0.0F) {
            this.vY = 0.0F;
        }

        if (this.killed) {
            this.color.a -= this.killSpeed * Gdx.graphics.getDeltaTime();
            if (this.color.a < 0.0F) {
                this.color.a = 0.0F;
            }
            this.scale += 5.0F * Gdx.graphics.getDeltaTime();
        }
    }

    public void kill() {
        this.killed = true;
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale, this.scale, this.rotation);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\FastSmoke.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

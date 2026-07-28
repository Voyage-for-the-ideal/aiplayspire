package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class IceShatterEffect
        extends AbstractGameEffect {
    private float x;
    private float y;

    public IceShatterEffect(float x, float y) {
        if (MathUtils.randomBoolean()) {
            this.img = ImageMaster.FROST_ACTIVATE_VFX_1;
        } else {
            this.img = ImageMaster.FROST_ACTIVATE_VFX_2;
        }

        this.x = x;
        this.y = y;
        this.vX = MathUtils.random(-300.0F, 300.0F) * Settings.scale;
        this.vY = MathUtils.random(-900.0F, 200.0F) * Settings.scale;
        this.duration = 0.5F;
        this.scale = MathUtils.random(0.75F, 1.25F) * Settings.scale;
        this.color = new Color(0.5F, 0.8F, 1.0F, MathUtils.random(0.9F, 1.0F));

        Vector2 derp = new Vector2(this.vX, this.vY);
        this.rotation = derp.angle() - 45.0F;
    }
    private float vX;
    private float vY;
    private Texture img;
    public void update() {
        this.x += this.vX * Gdx.graphics.getDeltaTime();
        this.y -= this.vY * Gdx.graphics.getDeltaTime();

        this.rotation += Gdx.graphics.getDeltaTime() * this.vX;
        this.vY += 2000.0F * Settings.scale * Gdx.graphics.getDeltaTime();

        this.color.a = this.duration * 2.0F;
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setBlendFunction(770, 1);
        sb.setColor(this.color);
        sb.draw(this.img, this.x, this.y, 32.0F, 32.0F, 64.0F, 64.0F, this.scale, this.scale, this.rotation, 0, 0, 64,
                64, false, false);
        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * IceShatterEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

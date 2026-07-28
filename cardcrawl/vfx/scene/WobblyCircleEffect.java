package com.megacrit.cardcrawl.vfx.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class WobblyCircleEffect
        extends AbstractGameEffect {
    private Vector2 pos = new Vector2(
            MathUtils.random(0, Settings.WIDTH),
            MathUtils.random(-100.0F, 500.0F) * Settings.scale + AbstractDungeon.floorY);
    private float vX = MathUtils.random(-72.0F, 72.0F) * Settings.scale;
    private float vY = MathUtils.random(-24.0F, 24.0F) * Settings.scale;
    private float aV;

    public WobblyCircleEffect() {
        float colorTmp = MathUtils.random(0.7F, 1.0F);
        this.color = new Color(MathUtils.random(0.7F, 0.8F), colorTmp, colorTmp, 0.0F);
        this.color.a = 0.0F;
        this.aV = MathUtils.random(0.2F, 0.4F);
    }
    private static final int W = 32;
    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        this.pos.add(this.vX * Gdx.graphics.getDeltaTime(), this.vY * Gdx.graphics.getDeltaTime());

        float dst = this.pos.dst(InputHelper.mX, InputHelper.mY);
        if (dst < 200.0F * Settings.scale) {
            this.pos.add((this.pos.x - InputHelper.mX) * Gdx.graphics
                    .getDeltaTime(), (this.pos.y - InputHelper.mY)
                            * Gdx.graphics
                                    .getDeltaTime());
        }

        if (this.duration < 0.0F) {
            this.isDone = true;
        }

        if (this.duration > this.startingDuration / 2.0F) {
            float tmp = this.duration - this.startingDuration / 2.0F;
            this.color.a = Interpolation.fade.apply(0.0F, 1.0F, this.startingDuration / 2.0F - tmp) * this.aV;
        } else {
            this.color.a = Interpolation.fade.apply(0.0F, 1.0F, this.duration / this.startingDuration / 2.0F) * this.aV;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.draw(ImageMaster.WOBBLY_ORB_VFX, this.pos.x - 16.0F, this.pos.y - 16.0F, 16.0F, 16.0F, 32.0F, 32.0F,
                this.scale, this.scale, 0.0F, 0, 0, 32, 32, false, false);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\scene\
 * WobblyCircleEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

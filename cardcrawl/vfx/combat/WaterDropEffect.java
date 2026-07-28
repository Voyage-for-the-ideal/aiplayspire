package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class WaterDropEffect extends AbstractGameEffect {
    private float x;
    private int frame = 0;
    private float y;
    private float animTimer = 0.1F;
    private static final int W = 64;

    public WaterDropEffect(float x, float y) {
        this.x = x;
        this.y = y;
        this.frame = 0;
        this.scale = MathUtils.random(2.5F, 3.0F) * Settings.scale;
        this.rotation = 0.0F;
        this.scale *= Settings.scale;
        this.color = new Color(1.0F, 0.05F, 0.05F, 0.0F);
    }

    public void update() {
        this.color.a = MathHelper.fadeLerpSnap(this.color.a, 1.0F);
        this.animTimer -= Gdx.graphics.getDeltaTime();
        if (this.animTimer < 0.0F) {
            this.animTimer += 0.1F;
            this.frame++;

            if (this.frame == 3) {
                for (int i = 0; i < 3; i++) {
                    AbstractDungeon.effectsQueue.add(new WaterSplashParticleEffect(this.x, this.y));
                }
            }

            if (this.frame > 5) {
                this.frame = 5;
                this.isDone = true;
            }
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        switch (this.frame) {
            case 0:
                sb.draw(ImageMaster.WATER_DROP_VFX[0], this.x - 32.0F, this.y - 32.0F + 40.0F * Settings.scale, 32.0F,
                        32.0F, 64.0F, 64.0F, this.scale, this.scale, this.rotation, 0, 0, 64, 64, false, false);
                break;

            case 1:
                sb.draw(ImageMaster.WATER_DROP_VFX[1], this.x - 32.0F, this.y - 32.0F + 20.0F * Settings.scale, 32.0F,
                        32.0F, 64.0F, 64.0F, this.scale, this.scale, this.rotation, 0, 0, 64, 64, false, false);
                break;

            case 2:
                sb.draw(ImageMaster.WATER_DROP_VFX[2], this.x - 32.0F, this.y - 32.0F + 10.0F * Settings.scale, 32.0F,
                        32.0F, 64.0F, 64.0F, this.scale, this.scale, this.rotation, 0, 0, 64, 64, false, false);
                break;

            case 3:
                sb.draw(ImageMaster.WATER_DROP_VFX[3], this.x - 32.0F, this.y - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F,
                        this.scale, this.scale, this.rotation, 0, 0, 64, 64, false, false);
                break;

            case 4:
                sb.draw(ImageMaster.WATER_DROP_VFX[4], this.x - 32.0F, this.y - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F,
                        this.scale, this.scale, this.rotation, 0, 0, 64, 64, false, false);
                break;

            case 5:
                sb.draw(ImageMaster.WATER_DROP_VFX[5], this.x - 32.0F, this.y - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F,
                        this.scale, this.scale, this.rotation, 0, 0, 64, 64, false, false);
                break;
        }
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * WaterDropEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

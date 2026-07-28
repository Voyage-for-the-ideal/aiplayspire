package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class HbBlockBrokenEffect extends AbstractGameEffect {
    private static final float WAIT_DUR = 0.4F;
    private static final float EFFECT_DUR = 0.7F;
    private static final int W = 64;
    private static final float DEST_X = -15.0F * Settings.scale;
    private static final float DEST_Y = -10.0F * Settings.scale;
    private static final float INITIAL_AV = 5.0F;
    private float x;
    private static final float INITIAL_VX = 5.0F * Settings.scale;

    private float y;
    private float offsetAngle;
    private float rotateSpeed = 55.0F;
    private float offsetX;
    private float offsetY;

    public HbBlockBrokenEffect(float x, float y) {
        this.color = new Color(0.6F, 0.93F, 0.98F, 1.0F);
        this.duration = 1.1F;
        this.x = x;
        this.y = y;
    }

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }

        if (this.duration < 0.7F) {
            this.offsetX = Interpolation.circleOut.apply(0.0F, DEST_X, 1.0F - this.duration / 0.7F);
            this.offsetY = Interpolation.fade.apply(0.0F, DEST_Y, 1.0F - this.duration / 0.7F);
            this.offsetAngle += Gdx.graphics.getDeltaTime() * this.rotateSpeed;
            this.color.a = Interpolation.fade.apply(1.0F, 0.0F, 1.0F - this.duration / 0.7F);
        } else {
            this.offsetX -= Gdx.graphics.getDeltaTime() * INITIAL_VX;
            this.offsetAngle += Gdx.graphics.getDeltaTime() * 5.0F;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);

        sb.draw(ImageMaster.BLOCK_ICON_L, this.x - 32.0F + this.offsetX, this.y - 32.0F + this.offsetY, 32.0F, 32.0F,
                64.0F, 64.0F, Settings.scale, Settings.scale, this.offsetAngle, 0, 0, 64, 64, false, false);

        sb.draw(ImageMaster.BLOCK_ICON_R, this.x - 32.0F - this.offsetX, this.y - 32.0F + this.offsetY, 32.0F, 32.0F,
                64.0F, 64.0F, Settings.scale, Settings.scale, -this.offsetAngle, 0, 0, 64, 64, false, false);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * HbBlockBrokenEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

package com.megacrit.cardcrawl.vfx.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class DefectVictoryEyesEffect
        extends AbstractGameEffect {
    private float x;
    private float y;
    private static Texture img;

    public DefectVictoryEyesEffect() {
        if (img == null) {
            img = ImageMaster.loadImage("images/vfx/defect/eyes2.png");
        }

        this.x = Settings.WIDTH / 2.0F;
        this.y = Settings.HEIGHT / 2.0F - 50.0F * Settings.scale;
        this.scale = 1.5F * Settings.scale;
        this.color = new Color(0.5F, 0.8F, 1.0F, 0.0F);
    }

    public void update() {
        this.color.a = MathHelper.slowColorLerpSnap(this.color.a, 0.5F);
        this.duration += Gdx.graphics.getDeltaTime();
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.setBlendFunction(770, 1);
        sb.draw(img, this.x - 512.0F, this.y - 180.0F, 512.0F, 180.0F, 1024.0F, 360.0F, this.scale * (

        MathUtils.cos(this.duration * 4.0F) / 20.0F + 1.0F), this.scale *
                MathUtils.random(0.99F, 1.01F), this.rotation, 0, 0, 1024, 360, false, false);

        sb.draw(img, this.x - 512.0F, this.y - 180.0F, 512.0F, 180.0F, 1024.0F, 360.0F, this.scale * (

        MathUtils.cos(this.duration * 5.0F) / 30.0F + 1.0F) * MathUtils.random(0.99F, 1.01F), this.scale *
                MathUtils.random(0.99F, 1.01F), this.rotation, 0, 0, 1024, 360, false, false);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\scene\
 * DefectVictoryEyesEffect.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */

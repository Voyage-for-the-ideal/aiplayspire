package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;

public class MapCircleEffect extends AbstractGameEffect {
    public static Texture img;
    private float x;
    private float y;
    public static final int W = 192;

    public MapCircleEffect(float x, float y, float angle) {
        img = ImageMaster.MAP_CIRCLE_1;
        this.x = x;
        this.y = y;
        this.scale = Settings.scale;
        this.duration = 1.2F;
        this.startingDuration = 1.2F;
        this.scale = 3.0F * Settings.scale;
        this.rotation = angle;
    }

    public void update() {
        if (Settings.FAST_MODE) {
            this.duration -= Gdx.graphics.getDeltaTime();
        }
        this.duration -= Gdx.graphics.getDeltaTime();

        if (this.duration < 1.0F) {
            img = ImageMaster.MAP_CIRCLE_5;
        } else if (this.duration < 1.05F) {
            img = ImageMaster.MAP_CIRCLE_4;
        } else if (this.duration < 1.1F) {
            img = ImageMaster.MAP_CIRCLE_3;
        } else if (this.duration < 1.15F) {
            img = ImageMaster.MAP_CIRCLE_2;
        }

        this.scale = MathHelper.scaleLerpSnap(this.scale, 1.5F * Settings.scale);

        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(new Color(0.09F, 0.13F, 0.17F, 1.0F));
        sb.draw(img, this.x - 96.0F, this.y - 96.0F, 96.0F, 96.0F, 192.0F, 192.0F, this.scale, this.scale,
                this.rotation, 0, 0, 192, 192, false, false);
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * MapCircleEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

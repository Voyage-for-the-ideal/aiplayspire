package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;

public class DoorFlashEffect
        extends AbstractGameEffect {
    private Texture img;
    private float yOffset = 0.0F;

    public DoorFlashEffect(Texture img, boolean eventVersion) {
        this.img = img;
        this.startingDuration = 1.3F;
        this.duration = this.startingDuration;
        this.color = Color.WHITE.cpy();
        this.scale = Settings.scale * 2.0F;

        if (eventVersion) {
            this.yOffset = -48.0F * Settings.scale;
        } else {
            this.yOffset = 0.0F;
        }
    }

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.duration = 0.0F;
            this.isDone = true;
        }

        this.color.a = Interpolation.fade.apply(0.0F, 1.0F, this.duration / this.startingDuration);
        this.scale = Interpolation.swingIn.apply(0.95F, 1.3F, this.duration / this.startingDuration) * Settings.scale;
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.setBlendFunction(770, 1);
        sb.draw(this.img, Settings.WIDTH / 2.0F - 960.0F, Settings.HEIGHT / 2.0F - 600.0F + this.yOffset, 960.0F,
                600.0F, 1920.0F, 1200.0F, this.scale, this.scale, 0.0F, 0, 0, 1920, 1200, false, false);

        sb.draw(this.img, Settings.WIDTH / 2.0F - 960.0F, Settings.HEIGHT / 2.0F - 600.0F + this.yOffset, 960.0F,
                600.0F, 1920.0F, 1200.0F, this.scale * 1.1F, this.scale * 1.1F, 0.0F, 0, 0, 1920, 1200, false, false);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
        this.img.dispose();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * DoorFlashEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

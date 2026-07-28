package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class BorderFlashEffect extends AbstractGameEffect {
    private TextureAtlas.AtlasRegion img = ImageMaster.BORDER_GLOW_2;

    private static final float DUR = 1.0F;

    private boolean additive;

    public BorderFlashEffect(Color color) {
        this(color, true);
    }

    public BorderFlashEffect(Color color, boolean additive) {
        this.duration = 1.0F;
        this.color = color.cpy();
        this.color.a = 0.0F;
        this.additive = additive;
    }

    public void update() {
        if (1.0F - this.duration < 0.1F) {
            this.color.a = Interpolation.fade.apply(0.0F, 1.0F, (1.0F - this.duration) * 10.0F);
        } else {
            this.color.a = Interpolation.pow2Out.apply(0.0F, 1.0F, this.duration);
        }

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        if (this.additive) {
            sb.setBlendFunction(770, 1);
            sb.setColor(this.color);
            sb.draw((TextureRegion) this.img, 0.0F, 0.0F, Settings.WIDTH, Settings.HEIGHT);
            sb.setBlendFunction(770, 771);
        } else {
            sb.setColor(this.color);
            sb.draw((TextureRegion) this.img, 0.0F, 0.0F, Settings.WIDTH, Settings.HEIGHT);
        }
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * BorderFlashEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

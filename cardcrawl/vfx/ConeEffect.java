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

public class ConeEffect extends AbstractGameEffect {
    private float x;
    private float y;

    public ConeEffect() {
        switch (MathUtils.random(1, 6)) {
            case 1:
                this.img = ImageMaster.CONE_3;
                break;
            default:
                if (MathUtils.randomBoolean()) {
                    this.img = ImageMaster.CONE_1;
                    break;
                }
                this.img = ImageMaster.CONE_2;
                break;
        }

        this.x = Settings.WIDTH / 2.0F;
        this.y = Settings.HEIGHT / 2.0F - this.img.packedHeight / 2.0F;
        this.duration = MathUtils.random(2.0F, 5.0F);
        this.startingDuration = this.duration;
        this.rotation = MathUtils.random(360.0F);
        this.aV = MathUtils.random(-10.0F, 10.0F);
        this.aV *= 2.0F;
        this.color = new Color(1.0F, MathUtils.random(0.7F, 0.8F), 0.2F, 0.0F);
        this.scale = Settings.scale;
    }
    private float aV;
    private TextureAtlas.AtlasRegion img;

    public void update() {
        this.rotation += this.aV * Gdx.graphics.getDeltaTime();
        this.duration -= Gdx.graphics.getDeltaTime();

        if (this.duration < 0.0F) {
            this.isDone = true;
        } else if (this.startingDuration - this.duration < 1.0F) {
            this.color.a = (this.startingDuration - this.duration) / 3.0F;
        } else if (this.duration < 1.0F) {
            this.color.a = Interpolation.fade.apply(0.0F, 0.33F, this.duration);
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.draw((TextureRegion) this.img, this.x, this.y, 0.0F, this.img.packedHeight / 2.0F, this.img.packedWidth,
                this.img.packedHeight, this.scale * 1.1F, this.scale * 1.1F, this.rotation);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\ConeEffect.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

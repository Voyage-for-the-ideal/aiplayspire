package com.megacrit.cardcrawl.scenes;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;

public class TitleCloud {
    private TextureAtlas.AtlasRegion region;
    private float x;
    private float y;

    public TitleCloud(TextureAtlas.AtlasRegion region, float vX, float x) {
        this.region = region;
        this.vX = vX;
        this.x = x;
        this.y = Settings.HEIGHT - 1100.0F * Settings.scale + MathUtils.random(-50.0F, 50.0F) * Settings.scale;
        this.vY = MathUtils.random(-vX / 10.0F, vX / 10.0F) * Settings.scale;
        this.sliderJiggle = MathUtils.random(-4.0F, 4.0F);
    }
    private float vX;
    private float vY;
    private float sliderJiggle;
    public void update() {
        this.x += this.vX * Gdx.graphics.getDeltaTime();
        this.y += this.vY * Gdx.graphics.getDeltaTime();

        if (this.vX > 0.0F && this.x > 1920.0F * Settings.xScale) {
            this.x = -1920.0F * Settings.xScale;
            this.vX = MathUtils.random(10.0F, 50.0F) * Settings.xScale;
            this.y = Settings.HEIGHT - 1100.0F * Settings.scale + MathUtils.random(-50.0F, 50.0F) * Settings.scale;
            this.vY = MathUtils.random(-this.vX / 5.0F, this.vX / 5.0F) * Settings.scale;
        } else if (this.x < -1920.0F * Settings.xScale) {
            this.x = 1920.0F * Settings.xScale;
            this.vX = MathUtils.random(-50.0F, -10.0F) * Settings.xScale;
            this.y = Settings.HEIGHT - 1100.0F * Settings.scale + MathUtils.random(-50.0F, 50.0F) * Settings.scale;
            this.vY = MathUtils.random(-this.vX / 5.0F, this.vX / 5.0F) * Settings.scale;
        }
    }

    public void render(SpriteBatch sb, float slider) {
        renderRegion(sb, this.region, this.x, (-55.0F + this.sliderJiggle) * Settings.scale * slider + this.y);
    }

    private void renderRegion(SpriteBatch sb, TextureAtlas.AtlasRegion region, float x, float y) {
        sb.draw(region
                .getTexture(), region.offsetX * Settings.scale + x, region.offsetY * Settings.scale + y, 0.0F, 0.0F,
                region.packedWidth, region.packedHeight, Settings.scale, Settings.scale, 0.0F, region

                        .getRegionX(),
                region
                        .getRegionY(),
                region
                        .getRegionWidth(),
                region
                        .getRegionHeight(),
                false, false);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\scenes\TitleCloud.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


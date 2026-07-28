package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class GameDeckGlowEffect extends AbstractGameEffect {
    private float effectDuration;
    private float x;
    private float y;
    private float vY;
    private Color shadowColor = Color.BLACK.cpy();
    private float vX;
    private float rotator;
    private boolean flipY;
    private boolean flipX;
    private TextureAtlas.AtlasRegion img;

    public GameDeckGlowEffect(boolean isAbove) {
        this.effectDuration = MathUtils.random(2.0F, 5.0F);
        this.duration = this.effectDuration;
        this.startingDuration = this.effectDuration;
        this.vY = MathUtils.random(10.0F * Settings.scale, 20.0F * Settings.scale);
        this.vX = MathUtils.random(10.0F * Settings.scale, 20.0F * Settings.scale);
        this.flipY = MathUtils.randomBoolean();
        this.flipX = MathUtils.randomBoolean();

        this.color = Settings.CREAM_COLOR.cpy();
        float darkness = MathUtils.random(0.1F, 0.4F);
        this.color.r -= darkness;
        this.color.g -= darkness;
        this.color.b -= darkness;
        this.img = getImg();
        this.x = MathUtils.random(35.0F, 85.0F) * Settings.scale - (this.img.packedWidth / 2);
        this.y = MathUtils.random(35.0F, 85.0F) * Settings.scale - (this.img.packedHeight / 2);
        this.scale = Settings.scale * 0.75F;
        this.rotator = MathUtils.random(-120.0F, 120.0F);
    }

    private TextureAtlas.AtlasRegion getImg() {
        int roll = MathUtils.random(0, 5);
        switch (roll) {
            case 0:
                return ImageMaster.DECK_GLOW_1;
            case 1:
                return ImageMaster.DECK_GLOW_2;
            case 2:
                return ImageMaster.DECK_GLOW_3;
            case 3:
                return ImageMaster.DECK_GLOW_4;
            case 4:
                return ImageMaster.DECK_GLOW_5;
        }
        return ImageMaster.DECK_GLOW_6;
    }

    public void update() {
        this.rotation += this.rotator * Gdx.graphics.getDeltaTime();
        if (this.vY != 0.0F) {
            if (this.flipY) {
                this.y += this.vY * Gdx.graphics.getDeltaTime();
            } else {
                this.y -= this.vY * Gdx.graphics.getDeltaTime();
            }
            this.vY = MathUtils.lerp(this.vY, 0.0F, Gdx.graphics.getDeltaTime() / 4.0F);
            if (this.vY < 0.5F) {
                this.vY = 0.0F;
            }
        }

        if (this.vX != 0.0F) {
            if (this.flipX) {
                this.x += this.vX * Gdx.graphics.getDeltaTime();
            } else {
                this.x -= this.vX * Gdx.graphics.getDeltaTime();
            }
            this.vX = MathUtils.lerp(this.vX, 0.0F, Gdx.graphics.getDeltaTime() / 4.0F);
            if (this.vX < 0.5F) {
                this.vX = 0.0F;
            }
        }

        this.duration -= Gdx.graphics.getDeltaTime();

        this.color.a = this.duration / this.effectDuration;
        if (this.duration < 0.0F) {
            this.isDone = true;
        }

        this.color.a /= 2.0F;
    }

    public void render(SpriteBatch sb, float x2, float y2) {
        if (this.img != null) {
            sb.setColor(this.color);
            sb.draw((TextureRegion) this.img, this.x + x2, this.y + y2, this.img.packedWidth / 2.0F,
                    this.img.packedHeight / 2.0F, this.img.packedWidth, this.img.packedHeight, this.scale, this.scale,
                    this.rotation);
        }
    }

    public void dispose() {
    }

    public void render(SpriteBatch sb) {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * GameDeckGlowEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

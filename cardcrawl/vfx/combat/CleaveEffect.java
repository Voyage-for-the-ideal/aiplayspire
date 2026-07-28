package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class CleaveEffect extends AbstractGameEffect {
    private float x;
    private float y;
    private float fadeInTimer = 0.05F;
    private float vX;
    private static final float FADE_IN_TIME = 0.05F;
    private static final float FADE_OUT_TIME = 0.4F;
    private float fadeOutTimer = 0.4F;
    private float stallTimer;
    private TextureAtlas.AtlasRegion img;

    public CleaveEffect(boolean usedByMonster) {
        this.img = ImageMaster.vfxAtlas.findRegion("combat/cleave");
        this.color = new Color(1.0F, 1.0F, 1.0F, 0.0F);
        this.x = Settings.WIDTH * 0.3F - this.img.packedWidth / 2.0F;
        this.y = AbstractDungeon.floorY + 100.0F * Settings.scale - this.img.packedHeight / 2.0F;
        this.vX = 100.0F * Settings.scale;
        this.stallTimer = MathUtils.random(0.0F, 0.2F);
        this.scale = 1.2F * Settings.scale;
        this.rotation = MathUtils.random(-5.0F, 1.0F);
    }

    public CleaveEffect() {
        this.img = ImageMaster.vfxAtlas.findRegion("combat/cleave");
        this.color = new Color(1.0F, 1.0F, 1.0F, 0.0F);
        this.x = Settings.WIDTH * 0.7F - this.img.packedWidth / 2.0F;
        this.y = AbstractDungeon.floorY + 100.0F * Settings.scale - this.img.packedHeight / 2.0F;
        this.vX = 100.0F * Settings.scale;
        this.stallTimer = MathUtils.random(0.0F, 0.2F);
        this.scale = 1.2F * Settings.scale;
        this.rotation = MathUtils.random(-5.0F, 1.0F);
    }

    public void update() {
        if (this.stallTimer > 0.0F) {
            this.stallTimer -= Gdx.graphics.getDeltaTime();

            return;
        }

        this.x += this.vX * Gdx.graphics.getDeltaTime();
        this.rotation += MathUtils.random(-0.5F, 0.5F);
        this.scale += 0.005F * Settings.scale;

        if (this.fadeInTimer != 0.0F) {
            this.fadeInTimer -= Gdx.graphics.getDeltaTime();
            if (this.fadeInTimer < 0.0F) {
                this.fadeInTimer = 0.0F;
            }
            this.color.a = Interpolation.fade.apply(1.0F, 0.0F, this.fadeInTimer / 0.05F);
        } else if (this.fadeOutTimer != 0.0F) {
            this.fadeOutTimer -= Gdx.graphics.getDeltaTime();
            if (this.fadeOutTimer < 0.0F) {
                this.fadeOutTimer = 0.0F;
            }
            this.color.a = Interpolation.pow2.apply(0.0F, 1.0F, this.fadeOutTimer / 0.4F);
        } else {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale, this.scale, this.rotation);

        sb.setBlendFunction(770, 1);
        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale, this.scale, this.rotation);

        sb.setBlendFunction(770, 771);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * CleaveEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

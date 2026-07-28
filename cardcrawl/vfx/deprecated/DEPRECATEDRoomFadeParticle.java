package com.megacrit.cardcrawl.vfx.deprecated;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class DEPRECATEDRoomFadeParticle
        extends AbstractGameEffect {
    private float x;
    private float y;
    private TextureAtlas.AtlasRegion img;
    private static final float DUR = 1.0F;

    public DEPRECATEDRoomFadeParticle(float y) {
        this.y = y;
        this.x = Settings.WIDTH + this.img.packedWidth * 1.5F;
        y -= (this.img.packedHeight / 2);
        this.duration = 1.0F;
        this.startingDuration = 1.0F;
        this.color = AbstractDungeon.fadeColor.cpy();
        this.color.a = 1.0F;
        this.scale *= 2.0F;
    }

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
        this.x = Interpolation.pow2Out.apply(0.0F - this.img.packedWidth * 1.5F,
                Settings.WIDTH + this.img.packedWidth * 1.5F, this.duration / 1.0F);
    }

    public void render(SpriteBatch sb) {
        sb.setColor(this.color);
        sb.draw((TextureRegion) this.img, this.x, this.y, this.img.packedWidth / 2.0F, this.img.packedHeight / 2.0F,
                this.img.packedWidth, this.img.packedHeight, this.scale, this.scale, this.rotation);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\deprecated\
 * DEPRECATEDRoomFadeParticle.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */

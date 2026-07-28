package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class LightBulbEffect extends AbstractGameEffect {
    private float x;
    private float y;

    public LightBulbEffect(Hitbox hb) {
        this.img = AbstractPower.atlas.findRegion("128/curiosity");
        this.x = hb.cX - this.img.packedHeight / 2.0F;
        this.y = hb.cY + hb.height / 2.0F - this.img.packedHeight / 2.0F;
        this.startY = this.y - 50.0F * Settings.scale;
        this.dstY = this.y + 70.0F * Settings.scale;
        this.startingDuration = 0.8F;
        this.duration = this.startingDuration;
        this.color = Color.WHITE.cpy();
        this.color.a = 0.0F;
    }
    private float startY;
    private float dstY;
    private TextureAtlas.AtlasRegion img;
    public void update() {
        this.y = Interpolation.swingIn.apply(this.dstY, this.startY, this.duration * 1.0F / this.startingDuration);
        this.duration -= Gdx.graphics.getDeltaTime();

        if (this.duration < this.startingDuration * 0.8F) {
            this.color.a = Interpolation.fade.apply(0.0F, 1.0F, this.duration * 1.0F / this.startingDuration / 0.5F);
        } else {
            this.color.a = MathHelper.fadeLerpSnap(this.color.a, 0.0F);
        }

        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.setBlendFunction(770, 1);
        sb.setColor(this.color);
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
 * LightBulbEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class BlockImpactLineEffect
        extends AbstractGameEffect {
    private static final float EFFECT_DUR = 0.5F;
    private float x;
    private float y;

    public BlockImpactLineEffect(float x, float y) {
        if (MathUtils.randomBoolean()) {
            this.img = ImageMaster.STRIKE_LINE;
        } else {
            this.img = ImageMaster.STRIKE_LINE_2;
        }
        this.duration = 0.5F;
        this.startingDuration = 0.5F;
        this.x = x - this.img.packedWidth / 2.0F;
        this.y = y - this.img.packedHeight / 2.0F;
        this.speed = MathUtils.random(20.0F * Settings.scale, 40.0F * Settings.scale);

        this.speedVector = new Vector2(MathUtils.random(-1.0F, 1.0F), MathUtils.random(-1.0F, 1.0F));
        this.speedVector.nor();
        this.speedVector.angle();
        this.rotation = this.speedVector.angle();
        this.speedVector.x *= this.speed;
        this.speedVector.y *= this.speed;

        if (MathUtils.randomBoolean()) {
            this.color = Color.LIGHT_GRAY.cpy();
        } else {
            this.color = Color.CYAN.cpy();
        }
    }
    private Vector2 speedVector;
    private float speed;
    private TextureAtlas.AtlasRegion img;
    public void update() {
        this.speed -= Gdx.graphics.getDeltaTime() * 60.0F;
        this.speedVector.nor();
        this.speedVector.x *= this.speed;
        this.speedVector.y *= this.speed;

        this.x += this.speedVector.x * Gdx.graphics.getDeltaTime() * 60.0F;
        this.y += this.speedVector.y * Gdx.graphics.getDeltaTime() * 60.0F;

        this.scale = Settings.scale * this.duration / 0.5F;
        super.update();
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
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * BlockImpactLineEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

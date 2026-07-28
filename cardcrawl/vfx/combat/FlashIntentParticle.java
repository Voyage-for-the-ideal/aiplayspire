package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class FlashIntentParticle
        extends AbstractGameEffect {
    private static final float DURATION = 1.0F;
    private static final float START_SCALE = 5.0F * Settings.scale;
    private float scale = 0.01F;

    private static int W;
    private Texture img;
    private Color shineColor = new Color(1.0F, 1.0F, 1.0F, 0.0F);
    private float x;
    private float y;

    public FlashIntentParticle(Texture img, AbstractMonster m) {
        this.duration = 1.0F;
        this.img = img;
        W = img.getWidth();
        this.x = m.intentHb.cX - W / 2.0F;
        this.y = m.intentHb.cY - W / 2.0F;
        this.renderBehind = true;
    }

    public void update() {
        this.scale = Interpolation.fade.apply(START_SCALE, 0.01F, this.duration);
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
        }
    }

    public void render(SpriteBatch sb, float x, float y) {
    }

    public void dispose() {
    }

    public void render(SpriteBatch sb) {
        sb.setBlendFunction(770, 1);
        this.shineColor.a = this.duration / 2.0F;
        sb.setColor(this.shineColor);
        sb.draw(this.img, this.x, this.y, W / 2.0F, W / 2.0F, W, W, this.scale, this.scale, 0.0F, 0, 0, W, W, false,
                false);
        sb.setBlendFunction(770, 771);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * FlashIntentParticle.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

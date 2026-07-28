package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class HemokinesisEffect extends AbstractGameEffect {
    private float x;
    private float y;

    public HemokinesisEffect(float sX, float sY, float tX, float tY) {
        this.x = sX;
        this.y = sY;
        this.tX = tX;
        this.tY = tY;

        this.scale = 0.12F;
        this.duration = 0.5F;
    }
    private float tX;
    private float tY;
    public void update() {
        this.scale -= Gdx.graphics.getDeltaTime();
        if (this.scale < 0.0F) {
            AbstractDungeon.effectsQueue.add(new HemokinesisParticle(this.x +

                    MathUtils.random(60.0F, -60.0F) * Settings.scale,
                    this.y +
                            MathUtils.random(60.0F, -60.0F) * Settings.scale,
                    this.tX, this.tY, AbstractDungeon.player.flipHorizontal));

            this.scale = 0.04F;
        }

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F)
            this.isDone = true;
    }

    public void render(SpriteBatch sb) {
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * HemokinesisEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

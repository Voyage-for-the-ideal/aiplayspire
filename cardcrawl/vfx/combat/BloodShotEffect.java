package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class BloodShotEffect extends AbstractGameEffect {
    private float sX;
    private float sY;
    private int count = 0;
    private float tX;
    private float tY;
    private float timer = 0.0F;

    public BloodShotEffect(float sX, float sY, float tX, float tY, int count) {
        this.sX = sX - 20.0F * Settings.scale;
        this.sY = sY + 80.0F * Settings.scale;
        this.tX = tX;
        this.tY = tY;
        this.count = count;
    }

    public void update() {
        this.timer -= Gdx.graphics.getDeltaTime();
        if (this.timer < 0.0F) {
            this.timer += MathUtils.random(0.05F, 0.15F);
            AbstractDungeon.effectsQueue.add(new BloodShotParticleEffect(this.sX, this.sY, this.tX, this.tY));
            this.count--;
            if (this.count == 0)
                this.isDone = true;
        }
    }

    public void render(SpriteBatch sb) {
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * BloodShotEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.BorderFlashEffect;

public class EmptyStanceEffect extends AbstractGameEffect {
    private int numParticles = 10;
    private float x;

    public EmptyStanceEffect(float x, float y) {
        this.duration = 0.0F;
        this.x = x;
        this.y = y;
    }
    private float y;

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();

        if (this.duration < 0.0F) {
            if (this.numParticles == 10) {
                AbstractDungeon.effectsQueue.add(new BorderFlashEffect(Color.SKY, true));
            }
            AbstractDungeon.effectsQueue.add(new EmptyStanceParticleEffect(this.x, this.y));
            AbstractDungeon.effectsQueue.add(new EmptyStanceParticleEffect(this.x, this.y));
            AbstractDungeon.effectsQueue.add(new EmptyStanceParticleEffect(this.x, this.y));
            this.numParticles--;
            if (this.numParticles <= 0)
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
 * EmptyStanceEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

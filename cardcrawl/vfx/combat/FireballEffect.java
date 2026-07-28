package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.FireBurstParticleEffect;
import com.megacrit.cardcrawl.vfx.GhostlyWeakFireEffect;

public class FireballEffect extends AbstractGameEffect {
    private static final float FIREBALL_INTERVAL = 0.016F;
    private float x;
    private float y;
    private float vfxTimer = 0.0F;
    private float startX;
    private float startY;
    private float targetX;
    private float targetY;

    public FireballEffect(float startX, float startY, float targetX, float targetY) {
        this.startingDuration = 0.5F;
        this.duration = 0.5F;
        this.startX = startX;
        this.startY = startY;
        this.targetX = targetX + MathUtils.random(-20.0F, 20.0F) * Settings.scale;
        this.targetY = targetY + MathUtils.random(-20.0F, 20.0F) * Settings.scale;
        this.x = startX;
        this.y = startY;
    }

    public void update() {
        this.x = Interpolation.fade.apply(this.targetX, this.startX, this.duration / this.startingDuration);
        this.y = Interpolation.fade.apply(this.targetY, this.startY, this.duration / this.startingDuration);

        this.vfxTimer -= Gdx.graphics.getDeltaTime();
        if (this.vfxTimer < 0.0F) {
            this.vfxTimer = 0.016F;
            AbstractDungeon.effectsQueue.add(new LightFlareParticleEffect(this.x, this.y, Color.CHARTREUSE));
            AbstractDungeon.effectsQueue.add(new FireBurstParticleEffect(this.x, this.y));
        }

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
            AbstractDungeon.effectsQueue.add(new GhostIgniteEffect(this.x, this.y));
            AbstractDungeon.effectsQueue.add(new GhostlyWeakFireEffect(this.x, this.y));
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
 * FireballEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

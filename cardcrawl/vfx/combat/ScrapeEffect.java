package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class ScrapeEffect
        extends AbstractGameEffect {
    private float x;

    public ScrapeEffect(float x, float y) {
        this.x = x;
        this.y = y;
        this.color = Color.MAROON;
        this.color2 = Color.SCARLET;
        this.startingDuration = 0.1F;
        this.duration = this.startingDuration;
    }
    private float y;
    private Color color2;

    public void update() {
        if (MathUtils.randomBoolean()) {
            CardCrawlGame.sound.playA("ATTACK_DAGGER_5", MathUtils.random(0.0F, -0.3F));
        } else {
            CardCrawlGame.sound.playA("ATTACK_DAGGER_6", MathUtils.random(0.0F, -0.3F));
        }

        float oX = -50.0F * Settings.scale;
        float oY = 20.0F * Settings.scale;
        float sX = -35.0F * Settings.scale;
        float sY = 20.0F * Settings.scale;
        float dX = -150.0F;
        float dY = -400.0F;
        float angle = 155.0F;

        AbstractDungeon.effectsQueue.add(new AnimatedSlashEffect(this.x + sX * 1.5F + oX, this.y + sY * 1.5F + oY, dX,
                dY, angle, this.color, this.color2));

        AbstractDungeon.effectsQueue.add(new AnimatedSlashEffect(this.x + sX * 0.5F + oX, this.y + sY * 0.5F + oY, dX,
                dY, angle, this.color, this.color2));

        AbstractDungeon.effectsQueue.add(new AnimatedSlashEffect(this.x - sX * 0.5F + oX, this.y - sY * 0.5F + oY, dX,
                dY, angle, this.color, this.color2));

        AbstractDungeon.effectsQueue.add(new AnimatedSlashEffect(this.x - sX * 1.5F + oX, this.y - sY * 1.5F + oY, dX,
                dY, angle, this.color, this.color2));

        oX = 50.0F * Settings.scale;
        oY = 20.0F * Settings.scale;
        sX = 35.0F * Settings.scale;
        sY = 20.0F * Settings.scale;
        dX = 150.0F;
        dY = -400.0F;
        angle = -155.0F;

        AbstractDungeon.effectsQueue.add(new AnimatedSlashEffect(this.x + sX * 1.5F + oX, this.y + sY * 1.5F + oY, dX,
                dY, angle, this.color, this.color2));

        AbstractDungeon.effectsQueue.add(new AnimatedSlashEffect(this.x + sX * 0.5F + oX, this.y + sY * 0.5F + oY, dX,
                dY, angle, this.color, this.color2));

        AbstractDungeon.effectsQueue.add(new AnimatedSlashEffect(this.x - sX * 0.5F + oX, this.y - sY * 0.5F + oY, dX,
                dY, angle, this.color, this.color2));

        AbstractDungeon.effectsQueue.add(new AnimatedSlashEffect(this.x - sX * 1.5F + oX, this.y - sY * 1.5F + oY, dX,
                dY, angle, this.color, this.color2));

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
 * ScrapeEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

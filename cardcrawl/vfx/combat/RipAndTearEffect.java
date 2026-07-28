package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class RipAndTearEffect
        extends AbstractGameEffect {
    private float x;
    private float y;

    public RipAndTearEffect(float x, float y, Color color1, Color color2) {
        this.x = x;
        this.y = y;
        this.color = color1;
        this.color2 = color2;
        this.startingDuration = 0.1F;
        this.duration = this.startingDuration;
    }
    private Color color2;
    private static boolean flipped = false;

    public void update() {
        if (MathUtils.randomBoolean()) {
            CardCrawlGame.sound.playA("ATTACK_DAGGER_5", MathUtils.random(0.0F, -0.3F));
        } else {
            CardCrawlGame.sound.playA("ATTACK_DAGGER_6", MathUtils.random(0.0F, -0.3F));
        }

        if (!flipped) {
            float baseAngle = 135.0F;
            AbstractDungeon.effectsQueue
                    .add(new AnimatedSlashEffect(this.x - 45.0F, this.y + 45.0F, -150.0F, -150.0F, baseAngle +

                            MathUtils.random(-10.0F, 10.0F), this.color, this.color2));

            AbstractDungeon.effectsQueue.add(new AnimatedSlashEffect(this.x, this.y, -150.0F, -150.0F, baseAngle +
                    MathUtils.random(-10.0F, 10.0F), this.color, this.color2));
            AbstractDungeon.effectsQueue
                    .add(new AnimatedSlashEffect(this.x + 45.0F, this.y - 45.0F, -150.0F, -150.0F, baseAngle +

                            MathUtils.random(-10.0F, 10.0F), this.color, this.color2));
        } else {

            float baseAngle = -135.0F;
            AbstractDungeon.effectsQueue
                    .add(new AnimatedSlashEffect(this.x - 45.0F, this.y - 45.0F, 150.0F, -150.0F, baseAngle +

                            MathUtils.random(-10.0F, 10.0F), this.color, this.color2));

            AbstractDungeon.effectsQueue.add(new AnimatedSlashEffect(this.x, this.y, 150.0F, -150.0F, baseAngle +
                    MathUtils.random(-10.0F, 10.0F), this.color, this.color2));
            AbstractDungeon.effectsQueue
                    .add(new AnimatedSlashEffect(this.x + 40.0F, this.y + 40.0F, 150.0F, -150.0F, baseAngle +

                            MathUtils.random(-10.0F, 10.0F), this.color, this.color2));
        }

        flipped = !flipped;

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
 * RipAndTearEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.DarkSmokePuffEffect;

public class ExplosionSmallEffect
        extends AbstractGameEffect {
    private static final int EMBER_COUNT = 12;

    public ExplosionSmallEffect(float x, float y) {
        this.x = x;
        this.y = y;
    }
    private float x;
    private float y;

    public void update() {
        AbstractDungeon.effectsQueue.add(new DarkSmokePuffEffect(this.x, this.y));
        for (int i = 0; i < 12; i++) {
            AbstractDungeon.effectsQueue.add(new SmokingEmberEffect(this.x +

                    MathUtils.random(-50.0F, 50.0F) * Settings.scale,
                    this.y +
                            MathUtils.random(-50.0F, 50.0F) * Settings.scale));
        }
        CardCrawlGame.sound.playA("ATTACK_FIRE", MathUtils.random(-0.2F, -0.1F));
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
 * ExplosionSmallEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class HealEffect
        extends AbstractGameEffect {
    private static final float X_JITTER = 120.0F * Settings.scale;
    private static final float Y_JITTER = 120.0F * Settings.scale;
    private static final float OFFSET_Y = -50.0F * Settings.scale;

    public HealEffect(float x, float y, int amount) {
        int roll = MathUtils.random(0, 2);
        if (roll == 0) {
            CardCrawlGame.sound.play("HEAL_1");
        } else if (roll == 1) {
            CardCrawlGame.sound.play("HEAL_2");
        } else {
            CardCrawlGame.sound.play("HEAL_3");
        }

        AbstractDungeon.effectsQueue.add(new HealNumberEffect(x, y, amount));

        for (int i = 0; i < 18; i++) {
            AbstractDungeon.effectsQueue.add(new HealVerticalLineEffect(x +

                    MathUtils.random(-X_JITTER * 1.5F, X_JITTER * 1.5F),
                    y + OFFSET_Y +
                            MathUtils.random(-Y_JITTER, Y_JITTER)));
        }
    }

    public void update() {
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
 * HealEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class DaggerSprayEffect extends AbstractGameEffect {
    private boolean flipX;

    public DaggerSprayEffect(boolean shouldFlip) {
        this.flipX = shouldFlip;
    }

    public void update() {
        this.isDone = true;

        if (this.flipX) {
            for (int i = 12; i > 0; i--) {
                float x = AbstractDungeon.player.hb.cX - MathUtils.random(0.0F, 450.0F) * Settings.scale;

                AbstractDungeon.effectsQueue.add(new FlyingDaggerEffect(x,
                        AbstractDungeon.player.hb.cY + 120.0F * Settings.scale + i * -18.0F * Settings.scale,
                        (i * 4) - 30.0F, true));

            }

        } else {

            for (int i = 0; i < 12; i++) {
                float x = AbstractDungeon.player.hb.cX + MathUtils.random(0.0F, 450.0F) * Settings.scale;

                AbstractDungeon.effectsQueue.add(new FlyingDaggerEffect(x,
                        AbstractDungeon.player.hb.cY - 100.0F * Settings.scale + i * 18.0F * Settings.scale,
                        (i * 4) - 20.0F, false));
            }
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
 * DaggerSprayEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

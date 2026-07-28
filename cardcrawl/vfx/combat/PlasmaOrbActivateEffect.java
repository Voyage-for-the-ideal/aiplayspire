package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class PlasmaOrbActivateEffect extends AbstractGameEffect {
    private float x;

    public PlasmaOrbActivateEffect(float x, float y) {
        this.x = x;
        this.y = y;
    }
    private float y;

    public void update() {
        for (int i = 0; i < 12; i++) {
            AbstractDungeon.effectsQueue.add(new PlasmaOrbActivateParticle(this.x, this.y));
        }
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
 * PlasmaOrbActivateEffect.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */

package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class WallopEffect extends AbstractGameEffect {
    private float x;
    private int damage = 0;
    private float y;

    public WallopEffect(int damage, float x, float y) {
        this.damage = damage;
        this.x = x;
        this.y = y;
        if (this.damage > 50) {
            this.damage = 50;
        }
    }

    public void update() {
        for (int i = 0; i < this.damage; i++) {
            AbstractDungeon.effectsQueue.add(new StarBounceEffect(this.x, this.y));
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
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\WallopEffect.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

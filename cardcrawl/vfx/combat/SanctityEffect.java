package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class SanctityEffect extends AbstractGameEffect {
    private float x;
    private int count = 10;
    private float y;
    private float vfxTimer;

    public SanctityEffect(float newX, float newY) {
        this.x = newX;
        this.y = newY;
    }

    public void update() {
        this.vfxTimer -= Gdx.graphics.getDeltaTime();
        if (this.vfxTimer < 0.0F) {
            this.count--;
            this.vfxTimer = MathUtils.random(0.0F, 0.02F);

            for (int i = 0; i < 3; i++) {
                AbstractDungeon.effectsQueue
                        .add(new LightRayFlyOutEffect(this.x, this.y, new Color(1.0F, 0.9F, 0.7F, 0.0F)));
            }
        }

        if (this.count <= 0)
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
 * SanctityEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

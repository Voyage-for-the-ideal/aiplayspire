package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class ClangClangClangEffect extends AbstractGameEffect {
    private float x;

    public ClangClangClangEffect(float x, float y) {
        this.x = x;
        this.y = y;
    }
    private float y;
    public void update() {
        for (int i = 0; i < 30; i++) {
            AbstractDungeon.effectsQueue.add(new UpgradeShineParticleEffect(this.x +

                    MathUtils.random(-10.0F, 10.0F) * Settings.scale,
                    this.y +
                            MathUtils.random(-10.0F, 10.0F) * Settings.scale));
        }
        this.isDone = true;
    }

    public void render(SpriteBatch sb) {
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * ClangClangClangEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

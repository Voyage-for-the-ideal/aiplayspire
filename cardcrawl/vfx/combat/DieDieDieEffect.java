package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class DieDieDieEffect extends AbstractGameEffect {
    private float interval = 0.0F;

    public DieDieDieEffect() {
        this.duration = 0.5F;
    }

    public void update() {
        this.interval -= Gdx.graphics.getDeltaTime();
        if (this.interval < 0.0F) {
            this.interval = MathUtils.random(0.02F, 0.05F);
            int derp = MathUtils.random(1, 4);

            for (int i = 0; i < derp; i++) {
                AbstractDungeon.effectsQueue.add(new ThrowShivEffect(

                        MathUtils.random(1200.0F, 2000.0F) * Settings.scale, AbstractDungeon.floorY +
                                MathUtils.random(-100.0F, 500.0F) * Settings.scale));
            }
        }

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F)
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
 * DieDieDieEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

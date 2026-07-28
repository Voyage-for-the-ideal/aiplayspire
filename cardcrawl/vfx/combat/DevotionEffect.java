package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.BorderLongFlashEffect;
import com.megacrit.cardcrawl.vfx.scene.TorchParticleXLEffect;

public class DevotionEffect
        extends AbstractGameEffect {
    int count = 0;

    public void update() {
        if (this.count == 0) {
            AbstractDungeon.effectsQueue.add(new BorderLongFlashEffect(Color.SKY, true));
        }
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.count++;
            this.duration = MathUtils.random(0.1F, 0.2F);
            float x = (Settings.WIDTH * this.count) / 7.0F;
            float y = MathUtils.random(AbstractDungeon.floorY - 80.0F * Settings.scale,
                    AbstractDungeon.floorY + 50.0F * Settings.scale);

            for (int i = 0; i < 5; i++) {
                AbstractDungeon.effectsQueue.add(new TorchParticleXLEffect(x, y, MathUtils.random(1.1F, 1.6F)));
            }
        }

        if (this.count >= 6)
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
 * DevotionEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

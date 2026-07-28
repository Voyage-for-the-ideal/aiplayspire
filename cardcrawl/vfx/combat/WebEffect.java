package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class WebEffect extends AbstractGameEffect {
    private float timer = 0.0F;
    private int count = 0;

    private AbstractCreature target;

    public WebEffect(AbstractCreature target, float srcX, float srcY) {
        this.target = target;
        this.srcX = srcX;
        this.srcY = srcY;
        this.duration = 1.0F;
    }
    private float srcX;
    private float srcY;
    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        this.timer -= Gdx.graphics.getDeltaTime();

        if (this.timer < 0.0F) {
            this.timer += 0.1F;

            switch (this.count) {
                case 0:
                    AbstractDungeon.effectsQueue.add(new WebLineEffect(this.srcX, this.srcY, true));
                    AbstractDungeon.effectsQueue.add(new WebLineEffect(this.srcX, this.srcY, true));
                    AbstractDungeon.effectsQueue.add(new WebParticleEffect(this.target.hb.cX - 90.0F * Settings.scale,
                            this.target.hb.cY - 10.0F * Settings.scale));
                    break;

                case 1:
                    AbstractDungeon.effectsQueue.add(new WebLineEffect(this.srcX, this.srcY, true));
                    AbstractDungeon.effectsQueue.add(new WebLineEffect(this.srcX, this.srcY, true));
                    break;
                case 2:
                    AbstractDungeon.effectsQueue.add(new WebLineEffect(this.srcX, this.srcY, true));
                    AbstractDungeon.effectsQueue.add(new WebLineEffect(this.srcX, this.srcY, true));
                    AbstractDungeon.effectsQueue.add(new WebParticleEffect(this.target.hb.cX + 70.0F * Settings.scale,
                            this.target.hb.cY + 80.0F * Settings.scale));
                    break;

                case 4:
                    AbstractDungeon.effectsQueue.add(new WebParticleEffect(this.target.hb.cX + 30.0F * Settings.scale,
                            this.target.hb.cY - 100.0F * Settings.scale));
                    break;
            }

            this.count++;
        }

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
 * WebEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

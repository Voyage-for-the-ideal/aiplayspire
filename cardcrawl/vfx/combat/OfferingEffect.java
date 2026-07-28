package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.BorderLongFlashEffect;

public class OfferingEffect extends AbstractGameEffect {
    private int count = 0;
    private float timer = 0.0F;

    public void update() {
        this.timer -= Gdx.graphics.getDeltaTime();
        if (this.timer < 0.0F) {
            this.timer += 0.3F;

            switch (this.count) {
                case 0:
                    CardCrawlGame.sound.playA("ATTACK_FIRE", -0.5F);
                    CardCrawlGame.sound.playA("BLOOD_SPLAT", -0.75F);
                    AbstractDungeon.effectsQueue.add(new BorderLongFlashEffect(new Color(1.0F, 0.1F, 0.1F, 1.0F)));
                    AbstractDungeon.effectsQueue.add(new WaterDropEffect(AbstractDungeon.player.hb.cX,
                            AbstractDungeon.player.hb.cY + 250.0F * Settings.scale));
                    break;

                case 1:
                    AbstractDungeon.effectsQueue
                            .add(new WaterDropEffect(AbstractDungeon.player.hb.cX + 150.0F * Settings.scale,
                                    AbstractDungeon.player.hb.cY - 80.0F * Settings.scale));
                    break;

                case 2:
                    AbstractDungeon.effectsQueue
                            .add(new WaterDropEffect(AbstractDungeon.player.hb.cX - 200.0F * Settings.scale,
                                    AbstractDungeon.player.hb.cY + 50.0F * Settings.scale));
                    break;

                case 3:
                    AbstractDungeon.effectsQueue
                            .add(new WaterDropEffect(AbstractDungeon.player.hb.cX + 200.0F * Settings.scale,
                                    AbstractDungeon.player.hb.cY + 50.0F * Settings.scale));
                    break;

                case 4:
                    AbstractDungeon.effectsQueue
                            .add(new WaterDropEffect(AbstractDungeon.player.hb.cX - 150.0F * Settings.scale,
                                    AbstractDungeon.player.hb.cY - 80.0F * Settings.scale));
                    break;
            }

            this.count++;

            if (this.count == 6)
                this.isDone = true;
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
 * OfferingEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

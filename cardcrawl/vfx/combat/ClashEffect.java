package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.UpgradeShineParticleEffect;

public class ClashEffect extends AbstractGameEffect {
    private float x;

    public ClashEffect(float x, float y) {
        this.x = x;
        this.y = y;
        this.startingDuration = 0.1F;
        this.duration = this.startingDuration;
    }
    private float y;

    public void update() {
        CardCrawlGame.sound.playA("ATTACK_WHIFF_1", 0.4F);
        CardCrawlGame.sound.playA("ATTACK_IRON_1", -0.1F);
        CardCrawlGame.sound.playA("ATTACK_IRON_3", -0.1F);
        AbstractDungeon.effectsQueue.add(new AnimatedSlashEffect(this.x, this.y - 30.0F * Settings.scale, -500.0F,
                -500.0F, 135.0F, 4.0F, Color.SCARLET, Color.GOLD));

        AbstractDungeon.effectsQueue.add(new AnimatedSlashEffect(this.x, this.y - 30.0F * Settings.scale, 500.0F,
                -500.0F, 225.0F, 4.0F, Color.SKY, Color.CYAN));

        for (int i = 0; i < 15; i++) {
            AbstractDungeon.effectsQueue.add(new UpgradeShineParticleEffect(this.x +

                    MathUtils.random(-40.0F, 40.0F) * Settings.scale,
                    this.y +
                            MathUtils.random(-40.0F, 40.0F) * Settings.scale));
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
 * ClashEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ScreenShake;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class ViolentAttackEffect extends AbstractGameEffect {
    private float x;
    private float y;
    private int count = 5;

    public ViolentAttackEffect(float x, float y, Color setColor) {
        this.x = x;
        this.y = y;
        this.duration = 0.0F;
        this.color = setColor;
        CardCrawlGame.screenShake.shake(ScreenShake.ShakeIntensity.MED, ScreenShake.ShakeDur.SHORT, false);
    }

    public void update() {
        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            CardCrawlGame.sound.playA("ATTACK_HEAVY", MathUtils.random(0.2F, 0.5F));
            AbstractDungeon.effectsQueue.add(new AnimatedSlashEffect(this.x +

                    MathUtils.random(-100.0F, 100.0F) * Settings.scale,
                    this.y +
                            MathUtils.random(-100.0F, 100.0F) * Settings.scale,
                    0.0F, 0.0F,

                    MathUtils.random(360.0F),
                    MathUtils.random(2.5F, 4.0F), this.color, this.color));

            if (MathUtils.randomBoolean()) {
                AbstractDungeon.effectsQueue.add(new FlashAtkImgEffect(this.x +

                        MathUtils.random(-150.0F, 150.0F) * Settings.scale,
                        this.y +
                                MathUtils.random(-150.0F, 150.0F) * Settings.scale,
                        AbstractGameAction.AttackEffect.BLUNT_HEAVY));
            } else {

                AbstractDungeon.effectsQueue.add(new FlashAtkImgEffect(this.x +

                        MathUtils.random(-150.0F, 150.0F) * Settings.scale,
                        this.y +
                                MathUtils.random(-150.0F, 150.0F) * Settings.scale,
                        AbstractGameAction.AttackEffect.BLUNT_LIGHT));
            }

            this.duration = MathUtils.random(0.05F, 0.1F);
            this.count--;
        }

        if (this.count == 0)
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
 * ViolentAttackEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

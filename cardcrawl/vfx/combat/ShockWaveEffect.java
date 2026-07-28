package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.ScreenShake;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class ShockWaveEffect extends AbstractGameEffect {
    private float x;
    private float y;
    private ShockWaveType type;
    private Color color;

    public ShockWaveEffect(float x, float y, Color color, ShockWaveType type) {
        this.x = x;
        this.y = y;
        this.type = type;
        this.color = color;
    }
    public void update() {
        int i;
        float speed = MathUtils.random(1000.0F, 1200.0F) * Settings.scale;
        switch (this.type) {
            case ADDITIVE:
                for (i = 0; i < 40; i++) {
                    AbstractDungeon.effectsQueue
                            .add(new BlurWaveAdditiveEffect(this.x, this.y, this.color.cpy(), speed));
                }
                break;
            case NORMAL:
                for (i = 0; i < 40; i++) {
                    AbstractDungeon.effectsQueue.add(new BlurWaveNormalEffect(this.x, this.y, this.color.cpy(), speed));
                }
                break;
            case CHAOTIC:
                CardCrawlGame.screenShake.shake(ScreenShake.ShakeIntensity.HIGH, ScreenShake.ShakeDur.SHORT, false);
                for (i = 0; i < 40; i++) {
                    AbstractDungeon.effectsQueue
                            .add(new BlurWaveChaoticEffect(this.x, this.y, this.color.cpy(), speed));
                }
                break;
        }
        this.isDone = true;
    }

    public enum ShockWaveType {
        ADDITIVE, NORMAL, CHAOTIC;
    }

    public void render(SpriteBatch sb) {
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * ShockWaveEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

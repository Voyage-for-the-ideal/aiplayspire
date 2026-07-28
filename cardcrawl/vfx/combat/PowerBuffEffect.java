package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class PowerBuffEffect extends AbstractGameEffect {
    private static final float TEXT_DURATION = 2.0F;
    private static final float STARTING_OFFSET_Y = 60.0F * Settings.scale;
    private static final float TARGET_OFFSET_Y = 100.0F * Settings.scale;

    private float x;
    private float y;

    public PowerBuffEffect(float x, float y, String msg) {
        this.duration = 2.0F;
        this.startingDuration = 2.0F;
        this.msg = msg;
        this.x = x;
        this.y = y;
        this.targetColor = Settings.GREEN_TEXT_COLOR;
        this.color = Color.WHITE.cpy();
        this.offsetY = STARTING_OFFSET_Y;
    }
    private float offsetY;
    private String msg;
    private Color targetColor;

    public void update() {
        if (this.duration == this.startingDuration && !Settings.DISABLE_EFFECTS) {
            int i;
            for (i = 0; i < 10; i++) {
                AbstractDungeon.effectsQueue.add(new FlyingSpikeEffect(this.x -

                        MathUtils.random(-120.0F, 120.0F) * Settings.scale,
                        this.y +
                                MathUtils.random(90.0F, 110.0F) * Settings.scale,
                        -90.0F, 0.0F,

                        MathUtils.random(-200.0F, -50.0F) * Settings.scale, Settings.GREEN_TEXT_COLOR));
            }

            for (i = 0; i < 10; i++) {
                AbstractDungeon.effectsQueue.add(new FlyingSpikeEffect(this.x -

                        MathUtils.random(-120.0F, 120.0F) * Settings.scale,
                        this.y +
                                MathUtils.random(90.0F, 110.0F) * Settings.scale,
                        90.0F, 0.0F,

                        MathUtils.random(200.0F, 50.0F) * Settings.scale, Settings.GREEN_TEXT_COLOR));
            }
        }

        this.offsetY = Interpolation.exp10In.apply(TARGET_OFFSET_Y, STARTING_OFFSET_Y, this.duration / 2.0F);
        this.color.r = Interpolation.pow2In.apply(this.targetColor.r, 1.0F, this.duration / this.startingDuration);
        this.color.g = Interpolation.pow2In.apply(this.targetColor.g, 1.0F, this.duration / this.startingDuration);
        this.color.b = Interpolation.pow2In.apply(this.targetColor.b, 1.0F, this.duration / this.startingDuration);
        this.color.a = Interpolation.exp10Out.apply(0.0F, 1.0F, this.duration / 2.0F);

        this.duration -= Gdx.graphics.getDeltaTime();
        if (this.duration < 0.0F) {
            this.isDone = true;
            this.duration = 0.0F;
        }
    }

    public void render(SpriteBatch sb) {
        FontHelper.renderFontCentered(sb, FontHelper.losePowerFont, this.msg, this.x, this.y + this.offsetY, this.color,
                1.25F);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * PowerBuffEffect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

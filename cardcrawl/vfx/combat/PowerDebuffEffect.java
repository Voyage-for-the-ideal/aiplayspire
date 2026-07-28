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

public class PowerDebuffEffect extends AbstractGameEffect {
    private static final float TEXT_DURATION = 2.0F;
    private static final float STARTING_OFFSET_Y = 60.0F * Settings.scale;
    private static final float TARGET_OFFSET_Y = 100.0F * Settings.scale;

    private float x;

    private float y;

    public PowerDebuffEffect(float x, float y, String msg) {
        this.duration = 2.0F;
        this.startingDuration = 2.0F;
        this.msg = msg;
        this.x = x;
        this.y = y;
        this.targetColor = Settings.RED_TEXT_COLOR;
        this.color = Color.WHITE.cpy();
        this.offsetY = STARTING_OFFSET_Y;
        this.scale = Settings.scale * 0.5F;
    }
    private float offsetY;
    private String msg;
    private Color targetColor;

    public void update() {
        if (this.duration == this.startingDuration && !Settings.DISABLE_EFFECTS) {
            int i;
            for (i = 0; i < 10; i++) {
                AbstractDungeon.effectsQueue.add(new FlyingSpikeEffect(this.x -

                        MathUtils.random(20.0F) * Settings.scale,
                        this.y +
                                MathUtils.random(40.0F, 160.0F) * Settings.scale,
                        MathUtils.random(360.0F),
                        MathUtils.random(50.0F, 400.0F) * Settings.scale, 0.0F, Settings.RED_TEXT_COLOR));
            }

            for (i = 0; i < 10; i++) {
                AbstractDungeon.effectsQueue.add(new FlyingSpikeEffect(this.x +

                        MathUtils.random(20.0F) * Settings.scale,
                        this.y +
                                MathUtils.random(40.0F, 160.0F) * Settings.scale,
                        MathUtils.random(360.0F),
                        MathUtils.random(-400.0F, -50.0F) * Settings.scale, 0.0F, Settings.RED_TEXT_COLOR));
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
 * PowerDebuffEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

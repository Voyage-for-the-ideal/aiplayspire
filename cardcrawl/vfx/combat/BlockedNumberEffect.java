package com.megacrit.cardcrawl.vfx.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;

public class BlockedNumberEffect
        extends AbstractGameEffect {
    private static final float EFFECT_DUR = 2.3F;
    private static final float GRAVITY_Y = 75.0F * Settings.scale;
    private float x;
    private float y;
    private String msg;
    private float scale = 1.0F;
    private float swayTimer = 0.0F;

    public BlockedNumberEffect(float x, float y, String msg) {
        this.duration = 2.3F;
        this.startingDuration = 2.3F;
        this.x = x;
        this.y = y;
        this.msg = msg;
        this.color = new Color(0.4F, 0.8F, 0.9F, 1.0F);
    }

    public void update() {
        this.swayTimer -= Gdx.graphics.getDeltaTime() * 4.0F;
        this.x += MathUtils.cos(this.swayTimer) * 2.0F;
        this.y += GRAVITY_Y * Gdx.graphics.getDeltaTime();

        super.update();
        this.scale = Settings.scale * this.duration / 2.3F * 3.0F;
    }

    public void render(SpriteBatch sb) {
        if (this.scale <= 0.0F) {
            this.scale = 0.01F;
        }
        FontHelper.damageNumberFont.getData().setScale(this.scale);
        FontHelper.renderFontCentered(sb, FontHelper.damageNumberFont, this.msg, this.x, this.y, this.color);
    }

    public void dispose() {
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\combat\
 * BlockedNumberEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

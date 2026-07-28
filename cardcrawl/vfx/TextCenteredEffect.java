package com.megacrit.cardcrawl.vfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;

public class TextCenteredEffect extends AbstractGameEffect {
    private static final float TEXT_DURATION = 1.8F;
    private static final float DRAW_X = Settings.WIDTH / 2.0F, DRAW_Y = Settings.HEIGHT * 0.6F;
    private static final float STARTING_OFFSET_Y = 120.0F * Settings.scale;
    private static final float TARGET_OFFSET_Y = 160.0F * Settings.scale;

    private static final float LERP_RATE = 5.0F;
    private float offsetY;
    private String msg;

    public TextCenteredEffect(String msg) {
        this.duration = 1.8F;
        this.startingDuration = 1.8F;
        this.msg = msg;
        this.color = Color.WHITE.cpy();
        this.offsetY = STARTING_OFFSET_Y;
    }

    public void update() {
        super.update();
        this.offsetY = MathUtils.lerp(this.offsetY, TARGET_OFFSET_Y, Gdx.graphics.getDeltaTime() * 5.0F);
    }

    public void render(SpriteBatch sb) {
        if (!this.isDone)
            FontHelper.renderFontCentered(sb, FontHelper.buttonLabelFont, this.msg, DRAW_X, DRAW_Y + this.offsetY,
                    this.color);
    }

    public void dispose() {
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\vfx\
 * TextCenteredEffect.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */

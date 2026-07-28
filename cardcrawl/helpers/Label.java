package com.megacrit.cardcrawl.helpers;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class Label
        extends AbstractDrawable {
    private BitmapFont font;
    private String msg;
    private Color color;
    private float x;
    private float y;
    private float scale;

    public Label(BitmapFont font, String msg, float x, float y, int z, float scale, Color color) {
        this.font = font;
        this.msg = msg;
        this.x = x;
        this.y = y;
        this.z = z;
        this.scale = scale;
        this.color = color;
    }

    public void render(SpriteBatch sb) {
        this.font.getData().setScale(this.scale);
        FontHelper.renderFontCentered(sb, this.font, this.msg, this.x, this.y, this.color);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\Label.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


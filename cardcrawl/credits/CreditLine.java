package com.megacrit.cardcrawl.credits;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;

public class CreditLine {
    public String text;
    private float x = Settings.WIDTH / 2.0F;

    private float y;

    public CreditLine(String text, float offset, boolean isHeader) {
        this.text = text;
        this.y = offset * Settings.scale;

        if (isHeader) {
            this.font = FontHelper.tipBodyFont;
            this.color = Settings.GOLD_COLOR.cpy();
        } else {
            this.font = FontHelper.panelNameFont;
            this.color = Settings.CREAM_COLOR.cpy();
        }
    }
    private BitmapFont font;
    private Color color;
    public CreditLine(String text, float offset, boolean isHeader, boolean left) {
        this.text = text;
        this.y = offset * Settings.scale;

        if (isHeader) {
            this.font = FontHelper.tipBodyFont;
            this.color = Settings.GOLD_COLOR.cpy();
        } else {
            this.font = FontHelper.panelNameFont;
            this.color = Settings.CREAM_COLOR.cpy();
        }

        if (left) {
            this.x = Settings.WIDTH * 0.4F;
        } else {
            this.x = Settings.WIDTH * 0.6F;
        }
    }

    public void render(SpriteBatch sb, float scrollY) {
        FontHelper.renderFontCentered(sb, this.font, this.text, this.x, this.y + scrollY, this.color);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\credits\CreditLine
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


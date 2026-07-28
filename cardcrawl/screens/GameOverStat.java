package com.megacrit.cardcrawl.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.helpers.input.InputHelper;

public class GameOverStat {
    public final String label;
    private static final float VALUE_X = 430.0F * Settings.scale;
    public final String description;
    public final String value;
    public boolean hidden = true;
    private Color color = Settings.CREAM_COLOR.cpy();
    private float offsetX = -50.0F * Settings.scale;
    public Hitbox hb = null;

    public GameOverStat() {
        this.label = null;
        this.description = null;
        this.value = null;
        this.hb = new Hitbox(250.0F * Settings.scale, 32.0F * Settings.scale);
        this.color.a = 0.0F;
    }

    public GameOverStat(String label, String description, String value) {
        this.label = label;

        if (description != null && description.equals("")) {
            this.description = null;
        } else {
            this.description = description;
        }

        this.hb = new Hitbox(250.0F * Settings.scale, 32.0F * Settings.scale);
        this.value = value;
        this.color.a = 0.0F;
    }

    public void update() {
        if (!this.hidden) {
            this.color.a = MathHelper.slowColorLerpSnap(this.color.a, 1.0F);
            this.offsetX = MathHelper.uiLerpSnap(this.offsetX, 0.0F);
            if (this.hb != null) {
                this.hb.update();
                if (this.hb.hovered && this.description != null) {
                    TipHelper.renderGenericTip(InputHelper.mX + 80.0F * Settings.scale, InputHelper.mY, this.label,
                            this.description);
                }
            }
        }
    }

    public void renderLine(SpriteBatch sb, boolean isWide, float y) {
        if (isWide) {
            sb.setColor(this.color);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, Settings.WIDTH / 2.0F - 525.0F * Settings.scale,
                    y - 10.0F * Settings.scale, 1050.0F * Settings.scale, 4.0F * Settings.scale);

            sb.setColor(new Color(0.0F, 0.0F, 0.0F, this.color.a / 4.0F));
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, Settings.WIDTH / 2.0F - 525.0F * Settings.scale,
                    y - 10.0F * Settings.scale, 1050.0F * Settings.scale, 1.0F * Settings.scale);

        } else {

            sb.setColor(this.color);
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, Settings.WIDTH / 2.0F - 222.0F * Settings.scale,
                    y - 10.0F * Settings.scale, 434.0F * Settings.scale, 4.0F * Settings.scale);

            sb.setColor(new Color(0.0F, 0.0F, 0.0F, this.color.a / 4.0F));
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, Settings.WIDTH / 2.0F - 222.0F * Settings.scale,
                    y - 10.0F * Settings.scale, 434.0F * Settings.scale, 1.0F * Settings.scale);
        }
    }

    public void render(SpriteBatch sb, float x, float y) {
        if (this.label == null || this.value == null) {
            return;
        }

        Color prevColor = null;
        if (this.hb != null && this.hb.hovered) {
            prevColor = this.color;
            this.color = new Color(0.0F, 0.9F, 0.0F, this.color.a);
        }

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.topPanelInfoFont, this.label, x + this.offsetX, y,
                this.color);

        FontHelper.renderFontRightTopAligned(sb, FontHelper.topPanelInfoFont, this.value, x + VALUE_X + this.offsetX, y,
                this.color);

        if (this.hb != null) {
            this.hb.move(x + 120.0F * Settings.scale, y - 8.0F * Settings.scale);
            this.hb.render(sb);

            if (this.hb.hovered)
                this.color = prevColor;
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\
 * GameOverStat.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


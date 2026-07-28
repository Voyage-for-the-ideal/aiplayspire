package com.megacrit.cardcrawl.map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.TipHelper;

public class LegendItem {
    private static final float ICON_X = 1575.0F * Settings.xScale;
    private static final float TEXT_X = 1670.0F * Settings.xScale;
    private static final float SPACE_Y = Settings.isMobile ? (64.0F * Settings.yScale) : (58.0F * Settings.yScale);
    private static final float OFFSET_Y = Settings.isMobile ? (110.0F * Settings.yScale) : (100.0F * Settings.yScale);
    private Texture img;
    private static final int W = 128;
    private int index;
    private String label;
    private String header;
    private String body;
    public Hitbox hb = new Hitbox(230.0F * Settings.xScale, SPACE_Y - 2.0F);

    public LegendItem(String label, Texture img, String tipHeader, String tipBody, int index) {
        this.label = label;
        this.img = img;
        this.header = tipHeader;
        this.body = tipBody;
        this.index = index;
    }

    public void update() {
        this.hb.update();
        if (this.hb.hovered) {
            TipHelper.renderGenericTip(1500.0F * Settings.xScale, 270.0F * Settings.scale, this.header, this.body);
        }
    }

    public void render(SpriteBatch sb, Color c) {
        sb.setColor(c);
        if (!Settings.isMobile) {
            if (this.hb.hovered) {
                sb.draw(this.img, ICON_X - 64.0F, Legend.Y - SPACE_Y * this.index + OFFSET_Y - 64.0F, 64.0F, 64.0F,
                        128.0F, 128.0F, Settings.scale / 1.2F, Settings.scale / 1.2F, 0.0F, 0, 0, 128, 128, false,
                        false);

            } else {

                sb.draw(this.img, ICON_X - 64.0F, Legend.Y - SPACE_Y * this.index + OFFSET_Y - 64.0F, 64.0F, 64.0F,
                        128.0F, 128.0F, Settings.scale / 1.65F, Settings.scale / 1.65F, 0.0F, 0, 0, 128, 128, false,
                        false);

            }

        } else if (this.hb.hovered) {
            sb.draw(this.img, ICON_X - 64.0F, Legend.Y - SPACE_Y * this.index + OFFSET_Y - 64.0F, 64.0F, 64.0F, 128.0F,
                    128.0F, Settings.scale, Settings.scale, 0.0F, 0, 0, 128, 128, false, false);

        } else {

            sb.draw(this.img, ICON_X - 64.0F, Legend.Y - SPACE_Y * this.index + OFFSET_Y - 64.0F, 64.0F, 64.0F, 128.0F,
                    128.0F, Settings.scale / 1.3F, Settings.scale / 1.3F, 0.0F, 0, 0, 128, 128, false, false);
        }

        if (Settings.isMobile) {
            FontHelper.panelNameFont.getData().setScale(1.2F);
        }
        FontHelper.renderFontLeftTopAligned(sb, FontHelper.panelNameFont, this.label, TEXT_X - 50.0F * Settings.scale,
                Legend.Y - SPACE_Y * this.index + OFFSET_Y + 13.0F * Settings.yScale, c);

        if (Settings.isMobile) {
            FontHelper.panelNameFont.getData().setScale(1.0F);
        }

        this.hb.move(TEXT_X, Legend.Y - SPACE_Y * this.index + OFFSET_Y);

        if (c.a != 0.0F)
            this.hb.render(sb);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\map\LegendItem.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


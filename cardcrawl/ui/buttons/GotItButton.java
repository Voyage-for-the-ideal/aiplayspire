package com.megacrit.cardcrawl.ui.buttons;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.input.InputHelper;

public class GotItButton {
    public Hitbox hb = new Hitbox(220.0F * Settings.scale, 60.0F * Settings.scale);
    private static final int W = 210;
    private static final int H = 52;

    public GotItButton(float x, float y) {
        x += 120.0F * Settings.scale;
        y -= 160.0F * Settings.scale;
        this.x = x;
        this.y = y;

        this.hb.move(x, y);
    }
    float x;
    float y;
    public void update() {
        this.hb.update();
        if (this.hb.justHovered) {
            CardCrawlGame.sound.play("UI_HOVER");
        }

        if (this.hb.hovered && InputHelper.justClickedLeft) {
            this.hb.clickStarted = true;
        }
    }

    public void render(SpriteBatch sb) {
        sb.draw(ImageMaster.FTUE_BTN, this.x - 105.0F, this.y - 26.0F, 105.0F, 26.0F, 210.0F, 52.0F, Settings.scale,
                Settings.scale, 0.0F, 0, 0, 210, 52, false, false);

        if (this.hb.hovered) {
            sb.setBlendFunction(770, 1);
            sb.setColor(new Color(1.0F, 1.0F, 1.0F, 0.25F));
            sb.draw(ImageMaster.FTUE_BTN, this.x - 105.0F, this.y - 26.0F, 105.0F, 26.0F, 210.0F, 52.0F, Settings.scale,
                    Settings.scale, 0.0F, 0, 0, 210, 52, false, false);

            sb.setBlendFunction(770, 771);
        }

        this.hb.render(sb);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcraw\\ui\buttons\
 * GotItButton.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


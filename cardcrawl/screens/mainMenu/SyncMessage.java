package com.megacrit.cardcrawl.screens.mainMenu;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;

public class SyncMessage {
    private static final String message = (CardCrawlGame.languagePack.getUIString("SyncMessage")).TEXT[0];
    private Color color = Settings.CREAM_COLOR.cpy();
    private static final float HIDE_X = Settings.WIDTH + 720.0F * Settings.scale;
    private static final float SHOW_X = Settings.WIDTH - 80.0F * Settings.scale;
    private float x = HIDE_X;
    private static final float y = Settings.HEIGHT - 80.0F * Settings.scale;
    public boolean isShowing = false;

    public void show() {
        this.isShowing = true;
    }

    public void hide() {
        this.isShowing = false;
    }

    public void update() {
        if (Settings.isDebug && InputHelper.justClickedRight) {
            toggle();
        }

        if (this.isShowing) {
            this.x = MathHelper.uiLerpSnap(this.x, SHOW_X);
            this.color.a = (MathUtils.cosDeg((float) (System.currentTimeMillis() / 3L % 360L)) + 1.25F) / 2.3F;
        } else {
            this.x = MathHelper.uiLerpSnap(this.x, HIDE_X);
            this.color.a = MathHelper.slowColorLerpSnap(this.color.a, 0.0F);
        }
    }

    public void toggle() {
        if (this.isShowing) {
            hide();
        } else {
            show();
        }
    }

    public void render(SpriteBatch sb) {
        if (this.color.a == 0.0F) {
            return;
        }

        FontHelper.renderFontRightTopAligned(sb, FontHelper.damageNumberFont, message, this.x, y, this.color);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\mainMenu\
 * SyncMessage.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


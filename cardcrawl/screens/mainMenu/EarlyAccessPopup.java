package com.megacrit.cardcrawl.screens.mainMenu;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;

public class EarlyAccessPopup {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("EarlyAccessPopup");
    public static final String[] TEXT = uiStrings.TEXT;
    public static boolean isUp = false;
    private boolean darken = false;
    private static Texture img = null;

    public EarlyAccessPopup() {
        if (img == null) {
            img = ImageMaster.loadImage("images/ui/eapopup.png");
        }
    }

    public void update() {
        if (!this.darken) {
            this.darken = true;
            CardCrawlGame.mainMenuScreen.darken();
        }

        if ((InputHelper.justClickedLeft || InputHelper.pressedEscape || CInputActionSet.select.isJustPressed())
                && CardCrawlGame.mainMenuScreen.screenColor.a == 0.8F) {

            CardCrawlGame.mainMenuScreen.bg.activated = true;
            isUp = false;
            CardCrawlGame.mainMenuScreen.lighten();
        }
    }

    public void render(SpriteBatch sb) {
        sb.setColor(Color.WHITE);
        sb.draw(img, 0.0F, 0.0F, Settings.WIDTH, Settings.HEIGHT);

        if (!Settings.isBeta) {
            FontHelper.renderFontCenteredTopAligned(sb, FontHelper.damageNumberFont, TEXT[0], Settings.WIDTH / 2.0F,
                    Settings.HEIGHT / 2.0F + 150.0F * Settings.scale, Settings.GOLD_COLOR);

            FontHelper.renderSmartText(sb, FontHelper.topPanelInfoFont, TEXT[2], 600.0F * Settings.scale,
                    Settings.HEIGHT / 2.0F + 50.0F * Settings.scale, 800.0F * Settings.scale, 32.0F * Settings.scale,
                    Settings.CREAM_COLOR);

        } else {

            FontHelper.renderFontCenteredTopAligned(sb, FontHelper.damageNumberFont, TEXT[1], Settings.WIDTH / 2.0F,
                    Settings.HEIGHT / 2.0F + 150.0F * Settings.scale, Settings.GOLD_COLOR);

            FontHelper.renderSmartText(sb, FontHelper.topPanelInfoFont, TEXT[3], 600.0F * Settings.scale,
                    Settings.HEIGHT / 2.0F + 50.0F * Settings.scale, 800.0F * Settings.scale, 32.0F * Settings.scale,
                    Settings.CREAM_COLOR);
        }

        if (!Settings.isControllerMode) {
            FontHelper.renderFontCentered(sb, FontHelper.buttonLabelFont, TEXT[4], Settings.WIDTH / 2.0F,
                    Settings.HEIGHT * 0.2F, new Color(1.0F, 0.9F, 0.4F, 0.5F +

                            MathUtils.cosDeg((float) (System.currentTimeMillis() / 4L % 360L)) / 5.0F));
        } else {
            FontHelper.renderFontCentered(sb, FontHelper.buttonLabelFont, TEXT[5], Settings.WIDTH / 2.0F,
                    Settings.HEIGHT * 0.2F, new Color(1.0F, 0.9F, 0.4F, 0.5F +

                            MathUtils.cosDeg((float) (System.currentTimeMillis() / 4L % 360L)) / 5.0F));

            sb.draw(CInputActionSet.select
                    .getKeyImg(), Settings.WIDTH / 2.0F - 32.0F - 110.0F * Settings.scale,
                    Settings.HEIGHT * 0.2F - 32.0F, 32.0F, 32.0F, 64.0F, 64.0F, Settings.scale, Settings.scale, 0.0F, 0,
                    0, 64, 64, false, false);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\screens\mainMenu\
 * EarlyAccessPopup.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


package com.megacrit.cardcrawl.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.localization.TutorialStrings;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.ui.buttons.GotItButton;

public class FtueTip {
    private static final TutorialStrings tutorialStrings = CardCrawlGame.languagePack.getTutorialString("FTUE Tips");
    private GotItButton button;
    private float x;
    private float y;
    private static final int W = 622;
    private static final int H = 284;
    public static final String[] LABEL = tutorialStrings.LABEL;

    private String header;

    private String body;

    private AbstractCard c;
    private AbstractCreature m;
    private AbstractPotion p;
    public TipType type = null;
    public FtueTip() {
    }

    public enum TipType {
        ENERGY, CREATURE, CARD, POTION, CARD_REWARD, INTENT, SHUFFLE, NO_FTUE, COMBAT, RELIC, MULTI, POWER;
    }

    public FtueTip(String header, String body, float x, float y, AbstractPotion potion) {
        openScreen(header, body, x, y);
        this.type = TipType.POTION;
        this.p = potion;
    }

    public FtueTip(String header, String body, float x, float y, TipType type) {
        openScreen(header, body, x, y);
        this.type = type;
    }

    public FtueTip(String header, String body, float x, float y, AbstractCard c) {
        openScreen(header, body, x, y);
        this.c = c;
        this.type = TipType.CARD;
    }

    public void openScreen(String header, String body, float x, float y) {
        this.header = header;
        this.body = body;
        this.x = x;
        this.y = y;
        this.c = null;
        this.m = null;
        this.p = null;
        this.button = new GotItButton(x, y);

        AbstractDungeon.player.releaseCard();
        if (AbstractDungeon.isScreenUp) {
            AbstractDungeon.dynamicBanner.hide();
            AbstractDungeon.previousScreen = AbstractDungeon.screen;
        }
        AbstractDungeon.isScreenUp = true;
        AbstractDungeon.screen = AbstractDungeon.CurrentScreen.FTUE;
        AbstractDungeon.overlayMenu.showBlackScreen();
    }

    public void update() {
        this.button.update();
        if (this.button.hb.clicked || CInputActionSet.proceed.isJustPressed()) {
            CInputActionSet.proceed.unpress();
            CardCrawlGame.sound.play("DECK_OPEN");
            if (this.type == TipType.POWER) {
                AbstractDungeon.cardRewardScreen.reopen();
            } else {
                AbstractDungeon.closeCurrentScreen();
            }
        }
    }
    public void render(SpriteBatch sb) {
        float pScale;
        sb.setColor(Color.WHITE);
        sb.draw(ImageMaster.FTUE, this.x - 311.0F, this.y - 142.0F, 311.0F, 142.0F, 622.0F, 284.0F, Settings.scale,
                Settings.scale, 0.0F, 0, 0, 622, 284, false, false);

        sb.setColor(new Color(1.0F, 1.0F, 1.0F,
                0.7F + (MathUtils.cosDeg((float) (System.currentTimeMillis() / 2L % 360L)) + 1.25F) / 5.0F));
        this.button.render(sb);

        FontHelper.renderFontLeftTopAligned(sb, FontHelper.topPanelInfoFont, LABEL[0] + this.header,
                this.x - 190.0F * Settings.scale, this.y + 80.0F * Settings.scale, Settings.GOLD_COLOR);

        FontHelper.renderSmartText(sb, FontHelper.tipBodyFont, this.body, this.x - 250.0F * Settings.scale,
                this.y + 20.0F * Settings.scale, 450.0F * Settings.scale, 26.0F * Settings.scale, Settings.CREAM_COLOR);

        FontHelper.renderFontRightTopAligned(sb, FontHelper.topPanelInfoFont, LABEL[1],
                this.x + 194.0F * Settings.scale, this.y - 150.0F * Settings.scale, Settings.GOLD_COLOR);

        switch (this.type) {
            case CARD:
                this.c.render(sb);
                break;
            case POWER:
                pScale = this.c.drawScale;
                this.c.drawScale = 1.0F;
                this.c.render(sb);
                this.c.drawScale = pScale;
                break;

            case CREATURE:
                if (this.m.isPlayer) {
                    this.m.render(sb);
                } else {
                    this.m.render(sb);
                }
                if (this.m.hb.hovered) {
                    this.m.renderPowerTips(sb);
                }
                break;
            case ENERGY:
                AbstractDungeon.overlayMenu.energyPanel.render(sb);
                break;
            case POTION:
                this.p.render(sb);
                break;

            case SHUFFLE:
                AbstractDungeon.overlayMenu.combatDeckPanel.render(sb);
                AbstractDungeon.overlayMenu.discardPilePanel.render(sb);
                break;
        }

        if (Settings.isControllerMode) {
            sb.setColor(Color.WHITE);
            sb.draw(CInputActionSet.proceed
                    .getKeyImg(), this.button.hb.cX - 32.0F + 130.0F * Settings.scale,
                    this.button.hb.cY - 32.0F + 2.0F * Settings.scale, 32.0F, 32.0F, 64.0F, 64.0F, Settings.scale,
                    Settings.scale, 0.0F, 0, 0, 64, 64, false, false);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcraw\\ui\FtueTip.class
 * Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


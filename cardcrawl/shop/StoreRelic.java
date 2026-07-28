package com.megacrit.cardcrawl.shop;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.controller.CInputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.relics.AbstractRelic;

public class StoreRelic {
    public AbstractRelic relic;
    private ShopScreen shopScreen;
    public int price;
    private int slot;
    public boolean isPurchased = false;
    private static final float RELIC_GOLD_OFFSET_X = -56.0F * Settings.scale;
    private static final float RELIC_GOLD_OFFSET_Y = -100.0F * Settings.scale;
    private static final float RELIC_PRICE_OFFSET_X = 14.0F * Settings.scale;
    private static final float RELIC_PRICE_OFFSET_Y = -62.0F * Settings.scale;
    private static final float GOLD_IMG_WIDTH = ImageMaster.UI_GOLD.getWidth() * Settings.scale;

    public StoreRelic(AbstractRelic relic, int slot, ShopScreen screenRef) {
        this.relic = relic;
        this.price = relic.getPrice();
        this.slot = slot;
        this.shopScreen = screenRef;
    }

    public void update(float rugY) {
        if (this.relic != null) {
            if (!this.isPurchased) {

                this.relic.currentX = 1000.0F * Settings.xScale + 150.0F * this.slot * Settings.xScale;
                this.relic.currentY = rugY + 400.0F * Settings.yScale;
                this.relic.hb.move(this.relic.currentX, this.relic.currentY);

                this.relic.hb.update();
                if (this.relic.hb.hovered) {
                    this.shopScreen.moveHand(this.relic.currentX - 190.0F * Settings.xScale,
                            this.relic.currentY - 70.0F * Settings.yScale);
                    if (InputHelper.justClickedLeft) {
                        this.relic.hb.clickStarted = true;
                    }
                    this.relic.scale = Settings.scale * 1.25F;
                } else {
                    this.relic.scale = MathHelper.scaleLerpSnap(this.relic.scale, Settings.scale);
                }

                if (this.relic.hb.hovered && InputHelper.justClickedRight) {
                    CardCrawlGame.relicPopup.open(this.relic);
                }
            }

            if (this.relic.hb.clicked || (this.relic.hb.hovered && CInputActionSet.select.isJustPressed())) {
                this.relic.hb.clicked = false;
                if (!Settings.isTouchScreen) {
                    purchaseRelic();
                } else if (AbstractDungeon.shopScreen.touchRelic == null) {
                    if (AbstractDungeon.player.gold < this.price) {
                        this.shopScreen.playCantBuySfx();
                        this.shopScreen.createSpeech(ShopScreen.getCantBuyMsg());
                    } else {
                        AbstractDungeon.shopScreen.confirmButton.hideInstantly();
                        AbstractDungeon.shopScreen.confirmButton.show();
                        AbstractDungeon.shopScreen.confirmButton.isDisabled = false;
                        AbstractDungeon.shopScreen.confirmButton.hb.clickStarted = false;
                        AbstractDungeon.shopScreen.touchRelic = this;
                    }
                }
            }
        }
    }

    public void purchaseRelic() {
        if (AbstractDungeon.player.gold >= this.price) {
            AbstractDungeon.player.loseGold(this.price);
            CardCrawlGame.sound.play("SHOP_PURCHASE", 0.1F);
            CardCrawlGame.metricData.addShopPurchaseData(this.relic.relicId);
            (AbstractDungeon.getCurrRoom()).relics.add(this.relic);
            this.relic.instantObtain(AbstractDungeon.player, AbstractDungeon.player.relics.size(), true);
            this.relic.flash();

            if (this.relic.relicId.equals("Membership Card")) {
                this.shopScreen.applyDiscount(0.5F, true);
            }
            if (this.relic.relicId.equals("Smiling Mask")) {
                ShopScreen.actualPurgeCost = 50;
            }

            for (AbstractCard c : this.shopScreen.coloredCards) {
                this.relic.onPreviewObtainCard(c);
            }
            for (AbstractCard c : this.shopScreen.colorlessCards) {
                this.relic.onPreviewObtainCard(c);
            }

            this.shopScreen.playBuySfx();
            this.shopScreen.createSpeech(ShopScreen.getBuyMsg());

            if (this.relic.relicId.equals("The Courier") || AbstractDungeon.player.hasRelic("The Courier")) {
                AbstractRelic tempRelic = AbstractDungeon.returnRandomRelicEnd(ShopScreen.rollRelicTier());

                while (tempRelic instanceof com.megacrit.cardcrawl.relics.OldCoin
                        || tempRelic instanceof com.megacrit.cardcrawl.relics.SmilingMask
                        || tempRelic instanceof com.megacrit.cardcrawl.relics.MawBank
                        || tempRelic instanceof com.megacrit.cardcrawl.relics.Courier) {
                    tempRelic = AbstractDungeon.returnRandomRelicEnd(ShopScreen.rollRelicTier());
                }
                this.relic = tempRelic;
                this.price = this.relic.getPrice();
                this.shopScreen.getNewPrice(this);
            } else {
                this.isPurchased = true;
            }

        } else {

            this.shopScreen.playCantBuySfx();
            this.shopScreen.createSpeech(ShopScreen.getCantBuyMsg());
        }
    }

    public void hide() {
        if (this.relic != null) {
            this.relic.currentY = Settings.HEIGHT + 200.0F * Settings.scale;
        }
    }

    public void render(SpriteBatch sb) {
        if (this.relic != null) {
            this.relic.renderWithoutAmount(sb, new Color(0.0F, 0.0F, 0.0F, 0.25F));

            sb.setColor(Color.WHITE);
            sb.draw(ImageMaster.UI_GOLD, this.relic.currentX + RELIC_GOLD_OFFSET_X,
                    this.relic.currentY + RELIC_GOLD_OFFSET_Y, GOLD_IMG_WIDTH, GOLD_IMG_WIDTH);

            Color color = Color.WHITE;
            if (this.price > AbstractDungeon.player.gold) {
                color = Color.SALMON;
            }
            FontHelper.renderFontLeftTopAligned(sb, FontHelper.tipHeaderFont,

                    Integer.toString(this.price), this.relic.currentX + RELIC_PRICE_OFFSET_X,
                    this.relic.currentY + RELIC_PRICE_OFFSET_Y, color);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\shop\StoreRelic.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class StrikeUpPower
        extends AbstractPower {
    public static final String POWER_ID = "StrikeUp";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("StrikeUp");

    public StrikeUpPower(AbstractCreature owner, int amt) {
        this.name = powerStrings.NAME;
        this.ID = "StrikeUp";
        this.owner = owner;
        this.amount = amt;
        updateDescription();
        loadRegion("accuracy");
        updateExistingStrikes();
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
    }

    public void stackPower(int stackAmount) {
        this.fontScale = 8.0F;
        this.amount += stackAmount;
        updateExistingStrikes();
    }

    private void updateExistingStrikes() {
        for (AbstractCard c : AbstractDungeon.player.hand.group) {
            if (c.hasTag(AbstractCard.CardTags.STRIKE)) {
                (CardLibrary.getCard(c.cardID)).baseDamage += this.amount;
            }
        }

        for (AbstractCard c : AbstractDungeon.player.drawPile.group) {
            if (c.hasTag(AbstractCard.CardTags.STRIKE)) {
                (CardLibrary.getCard(c.cardID)).baseDamage += this.amount;
            }
        }

        for (AbstractCard c : AbstractDungeon.player.discardPile.group) {
            if (c.hasTag(AbstractCard.CardTags.STRIKE)) {
                (CardLibrary.getCard(c.cardID)).baseDamage += this.amount;
            }
        }

        for (AbstractCard c : AbstractDungeon.player.exhaustPile.group) {
            if (c.hasTag(AbstractCard.CardTags.STRIKE)) {
                (CardLibrary.getCard(c.cardID)).baseDamage += this.amount;
            }
        }
    }

    public void onDrawOrDiscard() {
        for (AbstractCard c : AbstractDungeon.player.hand.group) {
            if (c.hasTag(AbstractCard.CardTags.STRIKE))
                (CardLibrary.getCard(c.cardID)).baseDamage += this.amount;
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * StrikeUpPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


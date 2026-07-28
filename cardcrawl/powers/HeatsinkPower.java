package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class HeatsinkPower extends AbstractPower {
    public static final String POWER_ID = "Heatsink";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Heatsink");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public HeatsinkPower(AbstractCreature owner, int drawAmt) {
        this.name = NAME;
        this.ID = "Heatsink";
        this.owner = owner;
        this.amount = drawAmt;
        updateDescription();
        loadRegion("heatsink");
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (card.type == AbstractCard.CardType.POWER) {
            flash();
            addToTop((AbstractGameAction) new DrawCardAction(this.owner, this.amount));
        }
    }

    public void stackPower(int stackAmount) {
        this.fontScale = 8.0F;
        this.amount += stackAmount;
    }

    public void updateDescription() {
        if (this.amount <= 1) {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
        } else {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[2];
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * HeatsinkPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


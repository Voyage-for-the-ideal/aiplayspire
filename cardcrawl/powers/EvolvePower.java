package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class EvolvePower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Evolve");
    public static final String POWER_ID = "Evolve";

    public EvolvePower(AbstractCreature owner, int drawAmt) {
        this.name = powerStrings.NAME;
        this.ID = "Evolve";
        this.owner = owner;
        this.amount = drawAmt;
        updateDescription();
        loadRegion("evolve");
    }

    public void updateDescription() {
        if (this.amount == 1) {
            this.description = powerStrings.DESCRIPTIONS[0];
        } else {
            this.description = powerStrings.DESCRIPTIONS[1] + this.amount + powerStrings.DESCRIPTIONS[2];
        }
    }

    public void onCardDraw(AbstractCard card) {
        if (card.type == AbstractCard.CardType.STATUS && !this.owner.hasPower("No Draw")) {
            flash();
            addToBot((AbstractGameAction) new DrawCardAction(this.owner, this.amount));
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\EvolvePower
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


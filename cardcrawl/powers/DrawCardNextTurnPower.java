package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class DrawCardNextTurnPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Draw Card");
    public static final String POWER_ID = "Draw Card";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public DrawCardNextTurnPower(AbstractCreature owner, int drawAmount) {
        this.name = NAME;
        this.ID = "Draw Card";
        this.owner = owner;
        this.amount = drawAmount;
        updateDescription();
        loadRegion("carddraw");
        this.priority = 20;
    }

    public void updateDescription() {
        if (this.amount > 1) {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
        } else {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[2];
        }
    }

    public void atStartOfTurnPostDraw() {
        flash();
        addToBot((AbstractGameAction) new DrawCardAction(this.owner, this.amount));
        addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "Draw Card"));
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * DrawCardNextTurnPower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


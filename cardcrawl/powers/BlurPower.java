package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class BlurPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Blur");
    public static final String POWER_ID = "Blur";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public BlurPower(AbstractCreature owner, int amount) {
        this.name = NAME;
        this.ID = "Blur";
        this.owner = owner;
        this.amount = amount;
        this.description = DESCRIPTIONS[0];
        loadRegion("blur");
        this.isTurnBased = true;
    }

    public void updateDescription() {
        if (this.amount == 1) {
            this.description = DESCRIPTIONS[0];
        } else {
            this.description = DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[2];
        }
    }

    public void atEndOfRound() {
        if (this.amount == 0) {
            addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "Blur"));
        } else {
            addToBot((AbstractGameAction) new ReducePowerAction(this.owner, this.owner, "Blur", 1));
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\BlurPower.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.unique.EstablishmentPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class EstablishmentPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("EstablishmentPower");
    public static final String POWER_ID = "EstablishmentPower";

    public EstablishmentPower(AbstractCreature owner, int strengthAmount) {
        this.name = powerStrings.NAME;
        this.ID = "EstablishmentPower";
        this.owner = owner;
        this.amount = strengthAmount;
        updateDescription();
        loadRegion("establishment");
        this.priority = 25;
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
    }

    public void atEndOfTurn(boolean isPlayer) {
        flash();
        addToBot((AbstractGameAction) new EstablishmentPowerAction(this.amount));
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * EstablishmentPower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


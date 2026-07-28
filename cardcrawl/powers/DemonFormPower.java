package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class DemonFormPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Demon Form");
    public static final String POWER_ID = "Demon Form";

    public DemonFormPower(AbstractCreature owner, int strengthAmount) {
        this.name = powerStrings.NAME;
        this.ID = "Demon Form";
        this.owner = owner;
        this.amount = strengthAmount;
        updateDescription();
        loadRegion("demonForm");
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
    }

    public void atStartOfTurnPostDraw() {
        flash();
        addToBot((AbstractGameAction) new ApplyPowerAction(this.owner, this.owner,
                new StrengthPower(this.owner, this.amount), this.amount));
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * DemonFormPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


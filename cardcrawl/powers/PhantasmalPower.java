package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class PhantasmalPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Phantasmal");
    public static final String POWER_ID = "Phantasmal";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public PhantasmalPower(AbstractCreature owner, int amount) {
        this.name = NAME;
        this.ID = "Phantasmal";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("phantasmal");
    }

    public void updateDescription() {
        if (this.amount == 1) {
            this.description = DESCRIPTIONS[0];
        } else {
            this.description = DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[2];
        }
    }

    public void atStartOfTurn() {
        flash();
        addToBot((AbstractGameAction) new ApplyPowerAction(this.owner, this.owner,
                new DoubleDamagePower(this.owner, 1, false), this.amount));
        addToBot((AbstractGameAction) new ReducePowerAction(this.owner, this.owner, "Phantasmal", 1));
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * PhantasmalPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


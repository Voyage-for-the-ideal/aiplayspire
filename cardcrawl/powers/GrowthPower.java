package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class GrowthPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("GrowthPower");
    public static final String POWER_ID = "GrowthPower";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    private boolean skipFirst = true;

    public GrowthPower(AbstractCreature owner, int strAmt) {
        this.name = NAME;
        this.ID = "GrowthPower";
        this.owner = owner;
        this.amount = strAmt;
        updateDescription();
        loadRegion("ritual");
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
    }

    public void atEndOfRound() {
        if (!this.skipFirst) {
            flash();
            addToBot((AbstractGameAction) new ApplyPowerAction(this.owner, this.owner,
                    new StrengthPower(this.owner, this.amount), this.amount));
        } else {
            this.skipFirst = false;
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\GrowthPower
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


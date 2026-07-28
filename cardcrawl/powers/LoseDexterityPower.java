package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class LoseDexterityPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("DexLoss");
    public static final String POWER_ID = "DexLoss";

    public LoseDexterityPower(AbstractCreature owner, int newAmount) {
        this.name = powerStrings.NAME;
        this.ID = "DexLoss";
        this.owner = owner;
        this.amount = newAmount;
        this.type = PowerType.DEBUFF;
        updateDescription();
        loadRegion("flex");
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
    }

    public void atEndOfTurn(boolean isPlayer) {
        flash();
        addToBot((AbstractGameAction) new ApplyPowerAction(this.owner, this.owner,
                new DexterityPower(this.owner, -this.amount), -this.amount));
        addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "DexLoss"));
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * LoseDexterityPower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


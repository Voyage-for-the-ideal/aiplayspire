package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class FrailPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Frail");
    public static final String POWER_ID = "Frail";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;
    private boolean justApplied = false;

    public FrailPower(AbstractCreature owner, int amount, boolean isSourceMonster) {
        this.name = NAME;
        this.ID = "Frail";
        this.owner = owner;
        this.amount = amount;
        this.priority = 10;
        updateDescription();
        loadRegion("frail");

        if (isSourceMonster) {
            this.justApplied = true;
        }

        this.type = PowerType.DEBUFF;
        this.isTurnBased = true;
    }

    public void atEndOfRound() {
        if (this.justApplied) {
            this.justApplied = false;

            return;
        }
        if (this.amount == 0) {
            addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "Frail"));
        } else {
            addToBot((AbstractGameAction) new ReducePowerAction(this.owner, this.owner, "Frail", 1));
        }
    }

    public void updateDescription() {
        if (this.amount == 1) {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
        } else {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[2];
        }
    }

    public float modifyBlock(float blockAmount) {
        return blockAmount * 0.75F;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\FrailPower.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


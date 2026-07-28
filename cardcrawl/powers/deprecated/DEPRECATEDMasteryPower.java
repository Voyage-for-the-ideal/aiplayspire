package com.megacrit.cardcrawl.powers.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.stances.AbstractStance;

public class DEPRECATEDMasteryPower extends AbstractPower {
    public static final String POWER_ID = "Mastery";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Mastery");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public DEPRECATEDMasteryPower(AbstractCreature owner, int amount) {
        this.name = NAME;
        this.ID = "Mastery";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("corruption");
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
    }

    public void onChangeStance(AbstractStance oldStance, AbstractStance newStance) {
        if (oldStance.ID.equals(newStance.ID) && !newStance.ID.equals("Neutral")) {
            flash();
            addToBot((AbstractGameAction) new GainEnergyAction(this.amount));
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\deprecated\
 * DEPRECATEDMasteryPower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


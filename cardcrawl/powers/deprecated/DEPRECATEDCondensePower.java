package com.megacrit.cardcrawl.powers.deprecated;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class DEPRECATEDCondensePower extends AbstractPower {
    public static final String POWER_ID = "DEPRECATEDCondense";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("DEPRECATEDCondense");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public DEPRECATEDCondensePower(AbstractCreature owner, int bufferAmt) {
        this.name = NAME;
        this.ID = "DEPRECATEDCondense";
        this.owner = owner;
        this.amount = bufferAmt;
        updateDescription();
        loadRegion("buffer");
    }

    public int onLoseHp(int damageAmount) {
        if (damageAmount > this.amount) {
            flash();
            return this.amount;
        }
        return damageAmount;
    }

    public void stackPower(int stackAmount) {
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\deprecated\
 * DEPRECATEDCondensePower.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */


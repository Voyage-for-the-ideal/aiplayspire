package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class ThieveryPower extends AbstractPower {
    public static final String POWER_ID = "Thievery";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Thievery");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public ThieveryPower(AbstractCreature owner, int stealAmount) {
        this.name = NAME;
        this.ID = "Thievery";
        this.owner = owner;
        this.amount = stealAmount;
        updateDescription();
        loadRegion("thievery");
    }

    public void updateDescription() {
        this.description = this.owner.name + DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * ThieveryPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


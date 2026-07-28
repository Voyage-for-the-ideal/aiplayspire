package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class SurroundedPower extends AbstractPower {
    public static final String POWER_ID = "Surrounded";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Surrounded");

    public SurroundedPower(AbstractCreature owner) {
        this.name = powerStrings.NAME;
        this.ID = "Surrounded";
        this.owner = owner;
        this.amount = -1;
        updateDescription();
        loadRegion("surrounded");
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0];
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * SurroundedPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


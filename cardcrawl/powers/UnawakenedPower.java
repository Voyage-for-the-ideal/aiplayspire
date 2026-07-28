package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class UnawakenedPower extends AbstractPower {
    public static final String POWER_ID = "Unawakened";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Unawakened");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public UnawakenedPower(AbstractCreature owner) {
        this.name = NAME;
        this.ID = "Unawakened";
        this.owner = owner;
        this.amount = -1;
        updateDescription();
        loadRegion("unawakened");
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0];
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * UnawakenedPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


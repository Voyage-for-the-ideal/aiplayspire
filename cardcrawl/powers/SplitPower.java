package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class SplitPower extends AbstractPower {
    public static final String POWER_ID = "Split";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Split");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public SplitPower(AbstractCreature owner) {
        this.name = NAME;
        this.ID = "Split";
        this.owner = owner;
        this.amount = -1;
        updateDescription();
        loadRegion("split");
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + FontHelper.colorString(this.owner.name, "y") + DESCRIPTIONS[1];
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\SplitPower.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


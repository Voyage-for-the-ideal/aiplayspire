package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class ResurrectPower extends AbstractPower {
    public static final String POWER_ID = "Life Link";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Life Link");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public ResurrectPower(AbstractCreature owner) {
        this.name = NAME;
        this.ID = "Life Link";
        this.owner = owner;
        updateDescription();
        loadRegion("regrow");
        this.type = PowerType.BUFF;
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0];
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * ResurrectPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


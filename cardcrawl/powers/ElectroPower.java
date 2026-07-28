package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class ElectroPower extends AbstractPower {
    public static final String POWER_ID = "Electro";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Electro");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public ElectroPower(AbstractCreature owner) {
        this.name = NAME;
        this.ID = "Electro";
        this.owner = owner;
        updateDescription();
        loadRegion("mastery");
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0];
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * ElectroPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


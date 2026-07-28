package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class BarricadePower extends AbstractPower {
    public static final String POWER_ID = "Barricade";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Barricade");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public BarricadePower(AbstractCreature owner) {
        this.name = NAME;
        this.ID = "Barricade";
        this.owner = owner;
        this.amount = -1;
        updateDescription();
        loadRegion("barricade");
    }

    public void updateDescription() {
        if (this.owner.isPlayer) {
            this.description = DESCRIPTIONS[0];
        } else {
            this.description = DESCRIPTIONS[1];
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * BarricadePower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


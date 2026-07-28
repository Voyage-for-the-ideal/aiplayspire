package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class RechargingCorePower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("RechargingCore");
    public static final String POWER_ID = "RechargingCore";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;
    private int turnTimer;

    public RechargingCorePower(AbstractCreature owner, int amount) {
        this.name = NAME;
        this.ID = "RechargingCore";
        this.owner = owner;
        this.amount = amount;
        this.turnTimer = 3;
        updateDescription();
        loadRegion("conserve");
        this.type = PowerType.BUFF;
        this.isTurnBased = true;
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.turnTimer;
        if (this.turnTimer == 1) {
            this.description += DESCRIPTIONS[1];
        } else {
            this.description += DESCRIPTIONS[2];
        }
        for (int i = 0; i < this.amount; i++) {
            this.description += DESCRIPTIONS[3];
        }
        this.description += " .";
    }

    public void atStartOfTurn() {
        updateDescription();
        if (this.turnTimer == 1) {
            flash();
            this.turnTimer = 3;
            addToBot((AbstractGameAction) new GainEnergyAction(this.amount));
        } else {
            this.turnTimer--;
        }
        updateDescription();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * RechargingCorePower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


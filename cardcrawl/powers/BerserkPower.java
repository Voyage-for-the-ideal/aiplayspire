package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.LocalizedStrings;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class BerserkPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Berserk");
    public static final String POWER_ID = "Berserk";

    public BerserkPower(AbstractCreature owner, int amount) {
        this.name = powerStrings.NAME;
        this.ID = "Berserk";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("berserk");
    }

    public void updateDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(powerStrings.DESCRIPTIONS[0]);
        for (int i = 0; i < this.amount; i++) {
            sb.append("[R] ");
        }
        sb.append(LocalizedStrings.PERIOD);
        this.description = sb.toString();
    }

    public void atStartOfTurn() {
        addToBot((AbstractGameAction) new GainEnergyAction(this.amount));
        flash();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * BerserkPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


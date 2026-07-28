package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.actions.watcher.ChangeStanceAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class WrathNextTurnPower extends AbstractPower {
    public static final String POWER_ID = "WrathNextTurnPower";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("WrathNextTurnPower");

    public WrathNextTurnPower(AbstractCreature owner) {
        this.name = powerStrings.NAME;
        this.ID = "WrathNextTurnPower";
        this.owner = owner;
        updateDescription();
        loadRegion("anger");
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0];
    }

    public void atStartOfTurn() {
        addToBot((AbstractGameAction) new ChangeStanceAction("Wrath"));
        addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, this));
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * WrathNextTurnPower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class CannotChangeStancePower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack
            .getPowerStrings("CannotChangeStancePower");
    public static final String POWER_ID = "CannotChangeStancePower";

    public CannotChangeStancePower(AbstractCreature owner) {
        this.name = powerStrings.NAME;
        this.ID = "CannotChangeStancePower";
        this.owner = owner;
        updateDescription();
        loadRegion("no_stance");
        this.type = PowerType.DEBUFF;
    }

    public void atEndOfTurn(boolean isPlayer) {
        if (isPlayer) {
            addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner,
                    "CannotChangeStancePower"));
        }
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0];
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * CannotChangeStancePower.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */


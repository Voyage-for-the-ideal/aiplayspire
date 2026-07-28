package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.PlatedArmorPower;

public class LiveForeverPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("AngelForm");
    public static final String POWER_ID = "AngelForm";

    public LiveForeverPower(AbstractCreature owner, int armorAmt) {
        this.name = powerStrings.NAME;
        this.ID = "AngelForm";
        this.owner = owner;
        this.amount = armorAmt;
        updateDescription();
        loadRegion("deva");
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
    }

    public void atEndOfTurn(boolean isPlayer) {
        flash();
        addToBot((AbstractGameAction) new ApplyPowerAction(this.owner, this.owner,
                (AbstractPower) new PlatedArmorPower(this.owner, this.amount), this.amount));
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * LiveForeverPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


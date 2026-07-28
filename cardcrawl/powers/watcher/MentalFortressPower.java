package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.stances.AbstractStance;

public class MentalFortressPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Controlled");
    public static final String POWER_ID = "Controlled";

    public MentalFortressPower(AbstractCreature owner, int amount) {
        this.name = powerStrings.NAME;
        this.ID = "Controlled";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("mental_fortress");
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
    }

    public void onChangeStance(AbstractStance oldStance, AbstractStance newStance) {
        if (!oldStance.ID.equals(newStance.ID)) {
            flash();
            addToBot((AbstractGameAction) new GainBlockAction(this.owner, this.owner, this.amount));
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * MentalFortressPower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


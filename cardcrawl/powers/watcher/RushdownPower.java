package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.stances.AbstractStance;

public class RushdownPower extends AbstractPower {
    public static final String POWER_ID = "Adaptation";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Adaptation");

    public RushdownPower(AbstractCreature owner, int amount) {
        this.name = powerStrings.NAME;
        this.ID = "Adaptation";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("rushdown");
    }

    public void updateDescription() {
        if (this.amount > 1) {
            this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[2];
        } else {
            this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
        }
    }

    public void onChangeStance(AbstractStance oldStance, AbstractStance newStance) {
        if (!oldStance.ID.equals(newStance.ID) && newStance.ID.equals("Wrath")) {
            flash();
            addToBot((AbstractGameAction) new DrawCardAction(this.owner, this.amount));
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * RushdownPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


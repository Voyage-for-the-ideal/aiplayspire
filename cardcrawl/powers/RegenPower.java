package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.unique.RegenAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class RegenPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Regeneration");
    public static final String POWER_ID = "Regeneration";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public RegenPower(AbstractCreature owner, int heal) {
        this.name = NAME;
        this.ID = "Regeneration";
        this.owner = owner;
        this.amount = heal;
        updateDescription();
        loadRegion("regen");
        this.type = PowerType.BUFF;
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
    }

    public void atEndOfTurn(boolean isPlayer) {
        flashWithoutSound();
        addToTop((AbstractGameAction) new RegenAction(this.owner, this.amount));
    }

    public void stackPower(int stackAmount) {
        super.stackPower(stackAmount);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\RegenPower.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


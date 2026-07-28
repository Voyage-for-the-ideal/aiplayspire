package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class NoDrawPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("No Draw");
    public static final String POWER_ID = "No Draw";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public NoDrawPower(AbstractCreature owner) {
        this.name = NAME;
        this.ID = "No Draw";
        this.owner = owner;
        this.type = PowerType.DEBUFF;
        this.amount = -1;
        this.description = DESCRIPTIONS[0];
        loadRegion("noDraw");
    }

    public void atEndOfTurn(boolean isPlayer) {
        if (isPlayer)
            addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "No Draw"));
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\NoDrawPower
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


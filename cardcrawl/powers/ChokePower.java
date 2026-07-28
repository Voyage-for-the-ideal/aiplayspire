package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.LoseHPAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class ChokePower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Choked");
    public static final String POWER_ID = "Choked";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public ChokePower(AbstractCreature owner, int amount) {
        this.name = NAME;
        this.ID = "Choked";
        this.owner = owner;
        this.amount = amount;
        this.description = DESCRIPTIONS[0] + amount + DESCRIPTIONS[1];
        loadRegion("choke");
        this.type = PowerType.DEBUFF;
    }

    public void atStartOfTurn() {
        addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "Choked"));
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        flash();
        addToBot((AbstractGameAction) new LoseHPAction(this.owner, null, this.amount));
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\ChokePower.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


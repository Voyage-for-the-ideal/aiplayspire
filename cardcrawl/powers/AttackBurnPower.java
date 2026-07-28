package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class AttackBurnPower extends AbstractPower {
    public static final String POWER_ID = "Attack Burn";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Attack Burn");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    private boolean justApplied = true;

    public AttackBurnPower(AbstractCreature owner, int amount) {
        this.name = NAME;
        this.ID = "Attack Burn";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("attackBurn");
        this.type = PowerType.DEBUFF;
        this.isTurnBased = true;
    }

    public void atEndOfRound() {
        if (this.justApplied) {
            this.justApplied = false;

            return;
        }
        addToBot((AbstractGameAction) new ReducePowerAction(this.owner, this.owner, "Attack Burn", 1));
    }

    public void updateDescription() {
        if (this.amount == 1) {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
        } else {
            this.description = DESCRIPTIONS[2] + this.amount + DESCRIPTIONS[3];
        }
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (card.type == AbstractCard.CardType.ATTACK) {
            action.exhaustCard = true;
            flash();
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * AttackBurnPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


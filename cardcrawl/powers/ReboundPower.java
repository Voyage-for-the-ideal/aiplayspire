package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class ReboundPower extends AbstractPower {
    public static final String POWER_ID = "Rebound";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Rebound");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;
    private boolean justEvoked = true;

    public ReboundPower(AbstractCreature owner) {
        this.name = NAME;
        this.ID = "Rebound";
        this.owner = owner;
        this.amount = 1;
        updateDescription();
        loadRegion("rebound");
        this.isTurnBased = true;
        this.type = PowerType.BUFF;
    }

    public void updateDescription() {
        if (this.amount > 1) {
            this.description = DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[2];
        } else {
            this.description = DESCRIPTIONS[0];
        }
    }

    public void onAfterUseCard(AbstractCard card, UseCardAction action) {
        if (this.justEvoked) {
            this.justEvoked = false;

            return;
        }
        if (card.type != AbstractCard.CardType.POWER) {
            flash();
            action.reboundCard = true;
        }

        addToBot((AbstractGameAction) new ReducePowerAction(this.owner, this.owner, "Rebound", 1));
    }

    public void atEndOfTurn(boolean isPlayer) {
        if (isPlayer)
            addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "Rebound"));
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * ReboundPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


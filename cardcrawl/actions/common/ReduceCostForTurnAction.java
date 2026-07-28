package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;

public class ReduceCostForTurnAction extends AbstractGameAction {
    private AbstractCard targetCard;

    public ReduceCostForTurnAction(AbstractCard card, int amt) {
        this.targetCard = card;
        this.amount = amt;
        this.startDuration = Settings.ACTION_DUR_FASTER;
        this.duration = this.startDuration;
    }

    public void update() {
        if (this.duration == this.startDuration &&
                this.targetCard.costForTurn > 0) {
            this.targetCard.setCostForTurn(this.targetCard.costForTurn - this.amount);
        }

        tickDuration();
        if (Settings.FAST_MODE)
            this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * ReduceCostForTurnAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




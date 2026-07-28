package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class EstablishmentPowerAction
        extends AbstractGameAction {
    private int discountAmount;

    public EstablishmentPowerAction(int discountAmount) {
        this.discountAmount = discountAmount;
    }

    public void update() {
        for (AbstractCard c : AbstractDungeon.player.hand.group) {
            if (c.selfRetain || c.retain) {
                c.modifyCostForCombat(-this.discountAmount);
            }
        }
        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * EstablishmentPowerAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




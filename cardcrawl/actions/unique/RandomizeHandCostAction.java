package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class RandomizeHandCostAction
        extends AbstractGameAction {
    private AbstractPlayer p = AbstractDungeon.player;

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {

            for (AbstractCard card : this.p.hand.group) {
                if (card.cost >= 0) {
                    int newCost = AbstractDungeon.cardRandomRng.random(3);
                    if (card.cost != newCost) {
                        card.cost = newCost;
                        card.costForTurn = card.cost;
                        card.isCostModified = true;
                    }
                }
            }
            this.isDone = true;

            return;
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * RandomizeHandCostAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.utility.DiscardToHandAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class AllCostToHandAction extends AbstractGameAction {
    private AbstractPlayer p;

    public AllCostToHandAction(int costToTarget) {
        this.p = AbstractDungeon.player;
        setValues((AbstractCreature) this.p, (AbstractCreature) AbstractDungeon.player, this.amount);
        this.actionType = ActionType.CARD_MANIPULATION;
        this.costTarget = costToTarget;
    }
    private int costTarget;

    public void update() {
        if (this.p.discardPile.size() > 0) {
            for (AbstractCard card : this.p.discardPile.group) {
                if (card.cost == this.costTarget || card.freeToPlayOnce) {
                    addToBot((AbstractGameAction) new DiscardToHandAction(card));
                }
            }
        }
        tickDuration();
        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * AllCostToHandAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




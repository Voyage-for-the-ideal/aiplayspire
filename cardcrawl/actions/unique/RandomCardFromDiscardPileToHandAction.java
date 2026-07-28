package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class RandomCardFromDiscardPileToHandAction
        extends AbstractGameAction {
    public RandomCardFromDiscardPileToHandAction() {
        this.p = AbstractDungeon.player;
        setValues((AbstractCreature) this.p, (AbstractCreature) AbstractDungeon.player, this.amount);
        this.actionType = ActionType.CARD_MANIPULATION;
    }
    private AbstractPlayer p;

    public void update() {
        if (this.p.discardPile.size() > 0) {
            AbstractCard card = this.p.discardPile.getRandomCard(AbstractDungeon.cardRandomRng);
            this.p.hand.addToHand(card);
            card.lighten(false);
            this.p.discardPile.removeCard(card);
            this.p.hand.refreshHandLayout();
        }
        tickDuration();
        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * RandomCardFromDiscardPileToHandAction.class Java compiler version: 8 (52.0)
 * JD-Core Version: 1.1.3
 */




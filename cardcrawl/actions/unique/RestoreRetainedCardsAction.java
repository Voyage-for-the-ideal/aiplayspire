package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

import java.util.Iterator;

public class RestoreRetainedCardsAction extends AbstractGameAction {
    private CardGroup group;

    public RestoreRetainedCardsAction(CardGroup group) {
        setValues((AbstractCreature) AbstractDungeon.player, this.source, -1);
        this.group = group;
    }

    public void update() {
        this.isDone = true;

        for (Iterator<AbstractCard> c = this.group.group.iterator(); c.hasNext();) {
            AbstractCard e = c.next();
            if (e.retain || e.selfRetain) {
                e.onRetained();
                AbstractDungeon.player.hand.addToTop(e);
                e.retain = false;
                c.remove();
            }
        }
        AbstractDungeon.player.hand.refreshHandLayout();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * RestoreRetainedCardsAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




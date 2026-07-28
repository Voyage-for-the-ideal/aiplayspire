package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DiscardAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInHandAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.tempCards.Shiv;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class BladeFuryAction
        extends AbstractGameAction {
    public BladeFuryAction(boolean upgraded) {
        this.upgrade = upgraded;
    }
    private boolean upgrade;

    public void update() {
        int theSize = AbstractDungeon.player.hand.size();

        if (this.upgrade) {
            AbstractCard s = (new Shiv()).makeCopy();
            s.upgrade();
            addToTop((AbstractGameAction) new MakeTempCardInHandAction(s, theSize));
        } else {
            addToTop((AbstractGameAction) new MakeTempCardInHandAction((AbstractCard) new Shiv(), theSize));
        }

        addToTop((AbstractGameAction) new DiscardAction((AbstractCreature) AbstractDungeon.player,
                (AbstractCreature) AbstractDungeon.player, theSize, false));

        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * BladeFuryAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




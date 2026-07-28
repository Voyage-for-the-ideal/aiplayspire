package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class TransmuteAction
        extends AbstractGameAction {
    private AbstractPlayer p = AbstractDungeon.player;

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {
            AbstractDungeon.actionManager.cleanCardQueue();
            if (this.p.hand.group.isEmpty()) {
                this.isDone = true;
                return;
            }
            CardGroup tmp = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);
            tmp.group.addAll(this.p.hand.group);
            this.p.hand.clear();
            for (AbstractCard c : tmp.group) {
                AbstractDungeon.transformCard(c);
                AbstractCard transformedCard = AbstractDungeon.getTransformedCard();
                this.p.hand.addToTop(transformedCard);
            }

            tickDuration();

            return;
        }
        this.p.hand.refreshHandLayout();
        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * TransmuteAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




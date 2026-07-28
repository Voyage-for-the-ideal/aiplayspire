package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class DiscardPileToHandAction extends AbstractGameAction {
    public static final String[] TEXT = (CardCrawlGame.languagePack.getUIString("DiscardPileToHandAction")).TEXT;
    private AbstractPlayer p;

    public DiscardPileToHandAction(int amount) {
        this.p = AbstractDungeon.player;
        setValues((AbstractCreature) this.p, (AbstractCreature) AbstractDungeon.player, amount);
        this.actionType = ActionType.CARD_MANIPULATION;
    }

    public void update() {
        if (this.p.hand.size() >= 10) {
            this.isDone = true;

            return;
        }
        if (this.p.discardPile.size() == 1) {
            AbstractCard card = this.p.discardPile.group.get(0);
            if (this.p.hand.size() < 10) {
                this.p.hand.addToHand(card);
                this.p.discardPile.removeCard(card);
            }
            card.lighten(false);
            this.p.hand.refreshHandLayout();
            this.isDone = true;

            return;
        }
        if (this.duration == 0.5F) {
            AbstractDungeon.gridSelectScreen.open(this.p.discardPile, this.amount, TEXT[0], false);
            tickDuration();

            return;
        }

        if (AbstractDungeon.gridSelectScreen.selectedCards.size() != 0) {
            for (AbstractCard c : AbstractDungeon.gridSelectScreen.selectedCards) {
                if (this.p.hand.size() < 10) {
                    this.p.hand.addToHand(c);
                    this.p.discardPile.removeCard(c);
                }
                c.lighten(false);
                c.unhover();
            }
            AbstractDungeon.gridSelectScreen.selectedCards.clear();
            this.p.hand.refreshHandLayout();

            for (AbstractCard c : this.p.discardPile.group) {
                c.unhover();
                c.target_x = CardGroup.DISCARD_PILE_X;
                c.target_y = 0.0F;
            }
            this.isDone = true;
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * DiscardPileToHandAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




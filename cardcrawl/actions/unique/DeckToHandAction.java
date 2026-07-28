package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class DeckToHandAction
        extends AbstractGameAction {
    public DeckToHandAction(int amount) {
        this.p = AbstractDungeon.player;
        setValues((AbstractCreature) this.p, (AbstractCreature) AbstractDungeon.player, amount);
        this.actionType = ActionType.CARD_MANIPULATION;
        this.duration = Settings.ACTION_DUR_MED;
    }
    private AbstractPlayer p;

    public void update() {
        if (this.duration == Settings.ACTION_DUR_MED) {
            AbstractDungeon.gridSelectScreen.open(this.p.drawPile, this.amount, "Select a card to add to your hand.",
                    false);
            tickDuration();

            return;
        }

        if (AbstractDungeon.gridSelectScreen.selectedCards.size() != 0) {
            for (AbstractCard c : AbstractDungeon.gridSelectScreen.selectedCards) {
                this.p.hand.addToHand(c);
                this.p.drawPile.removeCard(c);
                c.unhover();
            }
            AbstractDungeon.gridSelectScreen.selectedCards.clear();
            this.p.hand.refreshHandLayout();
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * DeckToHandAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




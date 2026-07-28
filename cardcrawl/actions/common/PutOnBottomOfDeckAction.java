package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class PutOnBottomOfDeckAction extends AbstractGameAction {
    private AbstractPlayer p;
    private boolean isRandom;
    public static int numPlaced;

    public PutOnBottomOfDeckAction(AbstractCreature target, AbstractCreature source, int amount, boolean isRandom) {
        this.target = target;
        this.p = (AbstractPlayer) target;
        setValues(target, source, amount);
        this.duration = Settings.ACTION_DUR_FAST;
        this.actionType = ActionType.CARD_MANIPULATION;
    }

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {
            if (this.p.hand.size() < this.amount) {
                this.amount = this.p.hand.size();
            }

            if (this.isRandom) {
                for (int i = 0; i < this.amount; i++) {
                    this.p.hand.moveToBottomOfDeck(this.p.hand.getRandomCard(AbstractDungeon.cardRandomRng));
                }
            } else {
                if (this.p.hand.group.size() > this.amount) {
                    numPlaced = this.amount;
                    AbstractDungeon.handCardSelectScreen.open("put on the bottom of your draw pile", this.amount,
                            false);
                    tickDuration();
                    return;
                }
                for (int i = 0; i < this.p.hand.size(); i++) {
                    this.p.hand.moveToBottomOfDeck(this.p.hand.getRandomCard(AbstractDungeon.cardRandomRng));
                }
            }
        }

        if (!AbstractDungeon.handCardSelectScreen.wereCardsRetrieved) {
            for (AbstractCard c : AbstractDungeon.handCardSelectScreen.selectedCards.group) {
                this.p.hand.moveToBottomOfDeck(c);
            }
            AbstractDungeon.player.hand.refreshHandLayout();
            AbstractDungeon.handCardSelectScreen.wereCardsRetrieved = true;
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * PutOnBottomOfDeckAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




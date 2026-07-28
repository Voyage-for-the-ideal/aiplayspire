package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.UIStrings;

public class PutOnDeckAction extends AbstractGameAction {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("PutOnDeckAction");
    public static final String[] TEXT = uiStrings.TEXT;

    private AbstractPlayer p;
    private boolean isRandom;
    public static int numPlaced;

    public PutOnDeckAction(AbstractCreature target, AbstractCreature source, int amount, boolean isRandom) {
        this.target = target;
        this.p = (AbstractPlayer) target;
        setValues(target, source, amount);
        this.actionType = ActionType.CARD_MANIPULATION;
        this.isRandom = isRandom;
    }

    public void update() {
        if (this.duration == 0.5F) {
            if (this.p.hand.size() < this.amount) {
                this.amount = this.p.hand.size();
            }

            if (this.isRandom) {
                for (int i = 0; i < this.amount; i++) {
                    this.p.hand.moveToDeck(this.p.hand.getRandomCard(AbstractDungeon.cardRandomRng), false);
                }
            } else {
                if (this.p.hand.group.size() > this.amount) {
                    numPlaced = this.amount;
                    AbstractDungeon.handCardSelectScreen.open(TEXT[0], this.amount, false);
                    tickDuration();
                    return;
                }
                for (int i = 0; i < this.p.hand.size(); i++) {
                    this.p.hand.moveToDeck(this.p.hand.getRandomCard(AbstractDungeon.cardRandomRng), this.isRandom);
                }
            }
        }

        if (!AbstractDungeon.handCardSelectScreen.wereCardsRetrieved) {
            for (AbstractCard c : AbstractDungeon.handCardSelectScreen.selectedCards.group) {
                this.p.hand.moveToDeck(c, false);
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
 * PutOnDeckAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




package com.megacrit.cardcrawl.actions.utility;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class ExhaustToHandAction extends AbstractGameAction {
    private AbstractCard card;

    public ExhaustToHandAction(AbstractCard card) {
        this.actionType = ActionType.CARD_MANIPULATION;
        this.card = card;
        this.duration = Settings.ACTION_DUR_FAST;
    }

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {
            if (AbstractDungeon.player.exhaustPile.contains(this.card) && AbstractDungeon.player.hand
                    .size() < 10) {
                AbstractDungeon.player.hand.addToHand(this.card);
                this.card.stopGlowing();
                this.card.unhover();
                this.card.unfadeOut();
                this.card.setAngle(0.0F, true);
                this.card.lighten(false);
                this.card.drawScale = 0.12F;
                this.card.targetDrawScale = 0.75F;
                this.card.applyPowers();
                AbstractDungeon.player.exhaustPile.removeCard(this.card);
            }

            AbstractDungeon.player.hand.refreshHandLayout();
            AbstractDungeon.player.hand.glowCheck();
        }

        tickDuration();
        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\utility\
 * ExhaustToHandAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




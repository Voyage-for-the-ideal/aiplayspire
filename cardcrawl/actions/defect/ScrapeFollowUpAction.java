package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class ScrapeFollowUpAction
        extends AbstractGameAction {
    public void update() {
        AbstractDungeon.actionManager.addToTop((AbstractGameAction) new WaitAction(0.4F));

        tickDuration();

        if (this.isDone)
            for (AbstractCard c : DrawCardAction.drawnCards) {
                if (c.costForTurn != 0 && !c.freeToPlayOnce) {
                    AbstractDungeon.player.hand.moveToDiscardPile(c);
                    c.triggerOnManualDiscard();
                    GameActionManager.incrementDiscard(false);
                }
            }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * ScrapeFollowUpAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




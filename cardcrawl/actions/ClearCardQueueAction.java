package com.megacrit.cardcrawl.actions;

import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.cardManip.ExhaustCardEffect;

public class ClearCardQueueAction
        extends AbstractGameAction {
    public void update() {
        for (CardQueueItem c : AbstractDungeon.actionManager.cardQueue) {
            if (AbstractDungeon.player.limbo.contains(c.card)) {
                AbstractDungeon.effectList.add(new ExhaustCardEffect(c.card));
                AbstractDungeon.player.limbo.group.remove(c.card);
            }
        }

        AbstractDungeon.actionManager.cardQueue.clear();
        this.isDone = true;
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\
 * ClearCardQueueAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




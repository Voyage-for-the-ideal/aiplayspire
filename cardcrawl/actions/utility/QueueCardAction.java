package com.megacrit.cardcrawl.actions.utility;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

@Deprecated
public class QueueCardAction extends AbstractGameAction {
    private AbstractCard card;

    public QueueCardAction() {
        this.duration = Settings.ACTION_DUR_FAST;
    }

    public QueueCardAction(AbstractCard card, AbstractCreature target) {
        this.duration = Settings.ACTION_DUR_FAST;
        this.card = card;
        this.target = target;
    }

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {
            if (this.card == null) {

                AbstractDungeon.actionManager.cardQueue.add(new CardQueueItem());

            } else if (!queueContains(this.card)) {
                AbstractDungeon.actionManager.cardQueue
                        .add(new CardQueueItem(this.card, (AbstractMonster) this.target));
            }

            this.isDone = true;
        }
    }

    private boolean queueContains(AbstractCard card) {
        for (CardQueueItem i : AbstractDungeon.actionManager.cardQueue) {
            if (i.card == card) {
                return true;
            }
        }
        return false;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\utility\
 * QueueCardAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




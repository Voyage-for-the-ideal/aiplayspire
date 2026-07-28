package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.relics.AbstractRelic;

public class ShuffleAction extends AbstractGameAction {
    private CardGroup group;
    private boolean triggerRelics;

    public ShuffleAction(CardGroup theGroup) {
        this(theGroup, false);
    }

    public ShuffleAction(CardGroup theGroup, boolean trigger) {
        setValues(null, null, 0);
        this.duration = 0.0F;
        this.actionType = ActionType.SHUFFLE;
        this.group = theGroup;
        this.triggerRelics = trigger;
    }

    public void update() {
        if (this.triggerRelics) {
            for (AbstractRelic r : AbstractDungeon.player.relics) {
                r.onShuffle();
            }
        }
        this.group.shuffle();
        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * ShuffleAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




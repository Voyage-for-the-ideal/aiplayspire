package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DiscardAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class CalculatedGambleAction extends AbstractGameAction {
    private float startingDuration;

    public CalculatedGambleAction(boolean upgraded) {
        this.target = (AbstractCreature) AbstractDungeon.player;
        this.actionType = ActionType.WAIT;
        this.startingDuration = Settings.ACTION_DUR_FAST;
        this.duration = Settings.ACTION_DUR_FAST;
        this.isUpgraded = upgraded;
    }
    private boolean isUpgraded;

    public void update() {
        if (this.duration == this.startingDuration) {
            int count = AbstractDungeon.player.hand.size();
            if (this.isUpgraded) {
                addToTop((AbstractGameAction) new DrawCardAction(this.target, count + 1));
                addToTop((AbstractGameAction) new DiscardAction(this.target, this.target, count, true));
            } else if (count != 0) {
                addToTop((AbstractGameAction) new DrawCardAction(this.target, count));
                addToTop((AbstractGameAction) new DiscardAction(this.target, this.target, count, true));
            }

            this.isDone = true;
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * CalculatedGambleAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




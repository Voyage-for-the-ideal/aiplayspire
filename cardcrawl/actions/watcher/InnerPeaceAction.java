package com.megacrit.cardcrawl.actions.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class InnerPeaceAction
        extends AbstractGameAction {
    public InnerPeaceAction(int amount) {
        this.amount = amount;
    }

    public void update() {
        if (AbstractDungeon.player.stance.ID.equals("Calm")) {
            addToTop((AbstractGameAction) new DrawCardAction(this.amount));
        } else {
            addToTop(new ChangeStanceAction("Calm"));
        }
        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\watcher\
 * InnerPeaceAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




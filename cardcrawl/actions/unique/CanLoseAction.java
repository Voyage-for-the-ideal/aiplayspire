package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class CanLoseAction
        extends AbstractGameAction {
    public void update() {
        (AbstractDungeon.getCurrRoom()).cannotLose = false;
        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * CanLoseAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




package com.megacrit.cardcrawl.actions.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class PerfectedFormAction
        extends AbstractGameAction {
    public void update() {
        this.isDone = true;
        boolean hadCalm = false;
        boolean hadCourage = false;
        boolean hadWrath = false;

        if (!AbstractDungeon.player.stance.ID.equals("Divinity"))
            ;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\watcher\
 * PerfectedFormAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




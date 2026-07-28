package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class GainGoldAction
        extends AbstractGameAction {
    public GainGoldAction(int amount) {
        this.amount = amount;
    }

    public void update() {
        AbstractDungeon.player.gainGold(this.amount);
        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * GainGoldAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




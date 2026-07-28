package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class MonsterStartTurnAction
        extends AbstractGameAction {
    private static final float DURATION = Settings.ACTION_DUR_FAST;

    public void update() {
        if (this.duration == DURATION) {
            this.isDone = true;
            (AbstractDungeon.getCurrRoom()).monsters.applyPreTurnLogic();
        }
        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * MonsterStartTurnAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




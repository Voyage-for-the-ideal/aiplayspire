package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class ChangeStateAction extends AbstractGameAction {
    private boolean called = false;
    private AbstractMonster m;
    private String stateName;

    public ChangeStateAction(AbstractMonster monster, String stateName) {
        this.actionType = ActionType.SPECIAL;
        this.m = monster;
        this.stateName = stateName;
    }

    public void update() {
        if (!this.called) {
            this.m.changeState(this.stateName);
            this.called = true;
            this.isDone = true;
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * ChangeStateAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




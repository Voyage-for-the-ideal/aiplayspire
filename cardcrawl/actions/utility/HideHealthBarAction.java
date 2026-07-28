package com.megacrit.cardcrawl.actions.utility;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.AbstractCreature;

public class HideHealthBarAction extends AbstractGameAction {
    public HideHealthBarAction(AbstractCreature owner) {
        this.actionType = ActionType.WAIT;
        this.source = owner;
    }

    public void update() {
        this.source.hideHealthBar();
        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\utility\
 * HideHealthBarAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class EscapeAction extends AbstractGameAction {
    public EscapeAction(AbstractMonster source) {
        setValues((AbstractCreature) source, (AbstractCreature) source);
        this.duration = 0.5F;
        this.actionType = ActionType.TEXT;
    }

    public void update() {
        if (this.duration == 0.5F) {
            AbstractMonster m = (AbstractMonster) this.source;
            m.escape();
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * EscapeAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




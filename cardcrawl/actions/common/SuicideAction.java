package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class SuicideAction extends AbstractGameAction {
    private AbstractMonster m;
    private boolean relicTrigger;

    public SuicideAction(AbstractMonster target) {
        this(target, true);
    }

    public SuicideAction(AbstractMonster target, boolean triggerRelics) {
        this.duration = 0.0F;
        this.actionType = ActionType.DAMAGE;
        this.m = target;
        this.relicTrigger = triggerRelics;
    }

    public void update() {
        if (this.duration == 0.0F) {
            this.m.gold = 0;
            this.m.currentHealth = 0;
            this.m.die(this.relicTrigger);
            this.m.healthBarUpdatedEvent();
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * SuicideAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




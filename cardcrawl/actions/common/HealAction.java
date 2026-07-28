package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;

public class HealAction
        extends AbstractGameAction {
    public HealAction(AbstractCreature target, AbstractCreature source, int amount) {
        setValues(target, source, amount);
        this.startDuration = this.duration;
        if (Settings.FAST_MODE) {
            this.duration = this.startDuration = Settings.ACTION_DUR_FAST;
        }
        this.actionType = ActionType.HEAL;
    }

    public HealAction(AbstractCreature target, AbstractCreature source, int amount, float duration) {
        this(target, source, amount);
        this.duration = this.startDuration = duration;
    }

    public void update() {
        if (this.duration == this.startDuration) {
            this.target.heal(this.amount);
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * HealAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




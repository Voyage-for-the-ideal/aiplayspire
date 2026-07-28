package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;

public class CorpseExplosionAction
        extends AbstractGameAction {
    public CorpseExplosionAction(AbstractCreature target, AbstractCreature source) {
        this.target = target;
        this.source = source;
        this.actionType = ActionType.WAIT;
        this.duration = Settings.ACTION_DUR_FAST;
    }

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {
            if (this.target.hasPower("Poison")) {

                addToTop((AbstractGameAction) new RemoveSpecificPowerAction(this.target, this.source, "Poison"));
            } else {
                this.isDone = true;
            }
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * CorpseExplosionAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.AbstractCreature;

public class RemoveAllBlockAction extends AbstractGameAction {
    private static final float DUR = 0.25F;

    public RemoveAllBlockAction(AbstractCreature target, AbstractCreature source) {
        setValues(target, source, this.amount);
        this.actionType = ActionType.BLOCK;
        this.duration = 0.25F;
    }

    public void update() {
        if (!this.target.isDying && !this.target.isDead &&
                this.duration == 0.25F &&
                this.target.currentBlock > 0) {
            this.target.loseBlock();
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * RemoveAllBlockAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




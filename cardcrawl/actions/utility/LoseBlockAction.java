package com.megacrit.cardcrawl.actions.utility;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.AbstractCreature;

public class LoseBlockAction
        extends AbstractGameAction {
    public LoseBlockAction(AbstractCreature target, AbstractCreature source, int amount) {
        setValues(target, source, amount);
        this.actionType = ActionType.BLOCK;
    }

    public void update() {
        if (this.duration == 0.5F) {
            if (this.target.currentBlock == 0) {
                this.isDone = true;

                return;
            }
            this.target.loseBlock(this.amount);
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\utility\
 * LoseBlockAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




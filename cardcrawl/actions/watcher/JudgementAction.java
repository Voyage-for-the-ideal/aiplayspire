package com.megacrit.cardcrawl.actions.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.InstantKillAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;

public class JudgementAction
        extends AbstractGameAction {
    private int cutoff;

    public JudgementAction(AbstractCreature target, int cutoff) {
        this.duration = Settings.ACTION_DUR_FAST;
        this.source = null;
        this.target = target;
        this.cutoff = cutoff;
    }

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST &&
                this.target.currentHealth <= this.cutoff
                && this.target instanceof com.megacrit.cardcrawl.monsters.AbstractMonster) {
            addToTop((AbstractGameAction) new InstantKillAction(this.target));
        }

        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\watcher\
 * JudgementAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




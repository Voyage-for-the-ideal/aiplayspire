package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.actions.common.HealAction;
import com.megacrit.cardcrawl.actions.common.LoseHPAction;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class StepThroughTimeAction
        extends AbstractGameAction {
    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {
            int diff = GameActionManager.playerHpLastTurn - AbstractDungeon.player.currentHealth;
            if (diff > 0) {
                addToTop((AbstractGameAction) new HealAction(this.source, this.source, diff));
            } else if (diff < 0) {
                addToTop((AbstractGameAction) new LoseHPAction(this.source, this.source, diff));
            }
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * StepThroughTimeAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




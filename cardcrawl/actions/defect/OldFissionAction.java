package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class OldFissionAction
        extends AbstractGameAction {
    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {
            int orbCount = AbstractDungeon.player.filledOrbCount();

            for (int i = 0; i < orbCount; i++) {
                addToBot(new AnimateOrbAction(1));
                addToBot(new EvokeOrbAction(1));
            }

            addToBot(new IncreaseMaxOrbAction(orbCount));
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * OldFissionAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




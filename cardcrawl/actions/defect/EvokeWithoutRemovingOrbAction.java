package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class EvokeWithoutRemovingOrbAction extends AbstractGameAction {
    private int orbCount;

    public EvokeWithoutRemovingOrbAction(int amount) {
        if (Settings.FAST_MODE) {
            this.startDuration = Settings.ACTION_DUR_FAST;
        } else {
            this.startDuration = Settings.ACTION_DUR_XFAST;
        }
        this.duration = this.startDuration;
        this.orbCount = amount;
        this.actionType = ActionType.DAMAGE;
    }

    public void update() {
        if (this.duration == this.startDuration) {
            for (int i = 0; i < this.orbCount; i++) {
                AbstractDungeon.player.evokeWithoutLosingOrb();
            }
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * EvokeWithoutRemovingOrbAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




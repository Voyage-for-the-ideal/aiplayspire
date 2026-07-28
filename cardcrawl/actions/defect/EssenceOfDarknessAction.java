package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.orbs.Dark;

public class EssenceOfDarknessAction extends AbstractGameAction {
    public EssenceOfDarknessAction(int potency) {
        this.duration = Settings.ACTION_DUR_FAST;
        this.amount = potency;
    }

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {
            for (int i = 0; i < AbstractDungeon.player.orbs.size(); i++) {
                for (int j = 0; j < this.amount; j++) {
                    AbstractDungeon.player.channelOrb((AbstractOrb) new Dark());
                }
            }

            if (Settings.FAST_MODE) {
                this.isDone = true;

                return;
            }
        }
        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * EssenceOfDarknessAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




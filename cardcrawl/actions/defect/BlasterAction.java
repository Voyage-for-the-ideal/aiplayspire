package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.orbs.AbstractOrb;

public class BlasterAction
        extends AbstractGameAction {
    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {
            int counter = 0;
            for (AbstractOrb o : AbstractDungeon.player.orbs) {
                if (!(o instanceof com.megacrit.cardcrawl.orbs.EmptyOrbSlot)) {
                    counter++;
                }
            }

            if (counter != 0) {
                addToBot((AbstractGameAction) new GainEnergyAction(counter));
            }
        }
        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * BlasterAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




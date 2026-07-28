package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class DecreaseMaxOrbAction
        extends AbstractGameAction {
    public DecreaseMaxOrbAction(int slotDecrease) {
        this.duration = Settings.ACTION_DUR_FAST;
        this.amount = slotDecrease;
        this.actionType = ActionType.BLOCK;
    }

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {
            for (int i = 0; i < this.amount; i++) {
                AbstractDungeon.player.decreaseMaxOrbSlots(1);
            }
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * DecreaseMaxOrbAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




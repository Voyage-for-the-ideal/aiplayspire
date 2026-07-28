package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class AggregateEnergyAction
        extends AbstractGameAction {
    private int divideAmount;

    public AggregateEnergyAction(int divideAmountNum) {
        this.duration = Settings.ACTION_DUR_FAST;
        this.divideAmount = divideAmountNum;
    }

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {
            AbstractDungeon.player.gainEnergy(AbstractDungeon.player.drawPile.size() / this.divideAmount);
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * AggregateEnergyAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




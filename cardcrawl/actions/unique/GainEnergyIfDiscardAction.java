package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class GainEnergyIfDiscardAction
        extends AbstractGameAction {
    public GainEnergyIfDiscardAction(int amount) {
        setValues((AbstractCreature) AbstractDungeon.player, (AbstractCreature) AbstractDungeon.player, 0);
        this.duration = Settings.ACTION_DUR_FAST;

        this.energyGain = amount;
    }
    private int energyGain;

    public void update() {
        if (GameActionManager.totalDiscardedThisTurn > 0) {
            AbstractDungeon.player.gainEnergy(this.energyGain);
            AbstractDungeon.actionManager.updateEnergyGain(this.energyGain);
            for (AbstractCard c : AbstractDungeon.player.hand.group) {
                c.triggerOnGainEnergy(this.energyGain, true);
            }
        }

        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * GainEnergyIfDiscardAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




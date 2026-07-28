package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;

public class GainEnergyAndEnableControlsAction
        extends AbstractGameAction {
    public GainEnergyAndEnableControlsAction(int amount) {
        setValues((AbstractCreature) AbstractDungeon.player, (AbstractCreature) AbstractDungeon.player, 0);

        this.energyGain = amount;
    }
    private int energyGain;

    public void update() {
        if (this.duration == 0.5F) {
            AbstractDungeon.player.gainEnergy(this.energyGain);
            AbstractDungeon.actionManager.updateEnergyGain(this.energyGain);
            for (AbstractCard c : AbstractDungeon.player.hand.group) {
                c.triggerOnGainEnergy(this.energyGain, false);
            }
            for (AbstractRelic r : AbstractDungeon.player.relics) {
                r.onEnergyRecharge();
            }
            for (AbstractPower p : AbstractDungeon.player.powers) {
                p.onEnergyRecharge();
            }
            AbstractDungeon.actionManager.turnHasEnded = false;
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * GainEnergyAndEnableControlsAction.class Java compiler version: 8 (52.0)
 * JD-Core Version: 1.1.3
 */




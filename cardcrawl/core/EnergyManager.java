package com.megacrit.cardcrawl.core;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;

public class EnergyManager {
    public int energy;
    public int energyMaster;

    public EnergyManager(int e) {
        this.energyMaster = e;
    }

    public void prep() {
        this.energy = this.energyMaster;
        EnergyPanel.totalCount = 0;
    }

    public void recharge() {
        if (AbstractDungeon.player.hasRelic("Ice Cream")) {
            if (EnergyPanel.totalCount > 0) {
                AbstractDungeon.player.getRelic("Ice Cream").flash();
                AbstractDungeon.actionManager.addToTop((AbstractGameAction) new RelicAboveCreatureAction(
                        (AbstractCreature) AbstractDungeon.player, AbstractDungeon.player
                                .getRelic("Ice Cream")));
            }
            EnergyPanel.addEnergy(this.energy);
        } else if (AbstractDungeon.player.hasPower("Conserve")) {
            if (EnergyPanel.totalCount > 0) {
                AbstractDungeon.actionManager
                        .addToTop((AbstractGameAction) new ReducePowerAction((AbstractCreature) AbstractDungeon.player,
                                (AbstractCreature) AbstractDungeon.player, "Conserve", 1));
            }

            EnergyPanel.addEnergy(this.energy);
        } else {
            EnergyPanel.setEnergy(this.energy);
        }
        AbstractDungeon.actionManager.updateEnergyGain(this.energy);
    }

    public void use(int e) {
        EnergyPanel.useEnergy(e);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\core\EnergyManager
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


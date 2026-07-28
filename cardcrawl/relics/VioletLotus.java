package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.stances.AbstractStance;

public class VioletLotus extends AbstractRelic {
    public static final String ID = "VioletLotus";

    public VioletLotus() {
        super("VioletLotus", "violet_lotus.png", RelicTier.BOSS, LandingSound.MAGICAL);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void onChangeStance(AbstractStance prevStance, AbstractStance newStance) {
        if (!prevStance.ID.equals(newStance.ID) && prevStance.ID.equals("Calm")) {
            flash();
            addToBot((AbstractGameAction) new GainEnergyAction(1));
        }
    }

    public AbstractRelic makeCopy() {
        return new VioletLotus();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\VioletLotus
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


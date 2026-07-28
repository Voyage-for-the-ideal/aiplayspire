package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class FissionAction
        extends AbstractGameAction {
    public FissionAction(boolean upgraded) {
        this.duration = Settings.ACTION_DUR_XFAST;
        this.actionType = ActionType.ENERGY;
        this.upgraded = upgraded;
    }
    private boolean upgraded = false;

    public void update() {
        if (this.duration == Settings.ACTION_DUR_XFAST) {
            int orbCount = AbstractDungeon.player.filledOrbCount();
            addToTop((AbstractGameAction) new DrawCardAction((AbstractCreature) AbstractDungeon.player, orbCount));
            addToTop((AbstractGameAction) new GainEnergyAction(orbCount));
            if (this.upgraded) {
                addToTop(new EvokeAllOrbsAction());
            } else {
                addToTop(new RemoveAllOrbsAction());
            }
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * FissionAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




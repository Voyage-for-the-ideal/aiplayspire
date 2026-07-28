package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.orbs.Plasma;

public class FluxAction
        extends AbstractGameAction {
    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {
            for (int i = 0; i < AbstractDungeon.player.orbs.size(); i++) {
                if (!(AbstractDungeon.player.orbs.get(i) instanceof com.megacrit.cardcrawl.orbs.EmptyOrbSlot)
                        && !(AbstractDungeon.player.orbs.get(i) instanceof Plasma)) {

                    Plasma plasma = new Plasma();
                    ((AbstractOrb) plasma).cX = ((AbstractOrb) AbstractDungeon.player.orbs.get(i)).cX;
                    ((AbstractOrb) plasma).cY = ((AbstractOrb) AbstractDungeon.player.orbs.get(i)).cY;
                    plasma.setSlot(i, AbstractDungeon.player.maxOrbs);
                    AbstractDungeon.player.orbs.set(i, plasma);
                }
            }
        }
        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * FluxAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




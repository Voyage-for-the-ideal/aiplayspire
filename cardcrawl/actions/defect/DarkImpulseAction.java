package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.orbs.AbstractOrb;

public class DarkImpulseAction
        extends AbstractGameAction {
    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST &&
                !AbstractDungeon.player.orbs.isEmpty()) {
            for (AbstractOrb o : AbstractDungeon.player.orbs) {
                if (o instanceof com.megacrit.cardcrawl.orbs.Dark) {
                    o.onStartOfTurn();
                    o.onEndOfTurn();
                }
            }

            if (AbstractDungeon.player.hasRelic("Cables")
                    && !(AbstractDungeon.player.orbs.get(0) instanceof com.megacrit.cardcrawl.orbs.EmptyOrbSlot)) {
                if (AbstractDungeon.player.orbs.get(0) instanceof com.megacrit.cardcrawl.orbs.Dark) {
                    ((AbstractOrb) AbstractDungeon.player.orbs.get(0)).onStartOfTurn();
                    ((AbstractOrb) AbstractDungeon.player.orbs.get(0)).onEndOfTurn();
                }
            }
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * DarkImpulseAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




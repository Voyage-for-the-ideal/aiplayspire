package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.orbs.AbstractOrb;

public class RedoAction
        extends AbstractGameAction {
    private AbstractOrb orb;

    public void update() {
        if (!AbstractDungeon.player.orbs.isEmpty()) {
            this.orb = AbstractDungeon.player.orbs.get(0);
            if (this.orb instanceof com.megacrit.cardcrawl.orbs.EmptyOrbSlot) {
                this.isDone = true;
            } else {
                addToTop(new ChannelAction(this.orb, false));
                addToTop(new EvokeOrbAction(1));
            }
        }

        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * RedoAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




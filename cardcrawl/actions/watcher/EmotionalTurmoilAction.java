package com.megacrit.cardcrawl.actions.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class EmotionalTurmoilAction
        extends AbstractGameAction {
    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {

            if (AbstractDungeon.player.stance.ID.equals("Calm")) {
                addToBot(new ChangeStanceAction("Wrath"));
            } else if (AbstractDungeon.player.stance.ID.equals("Wrath")) {
                addToBot(new ChangeStanceAction("Calm"));
            }

            if (Settings.FAST_MODE) {
                this.isDone = true;

                return;
            }
        }
        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\watcher\
 * EmotionalTurmoilAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




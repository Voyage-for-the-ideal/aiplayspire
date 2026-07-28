package com.megacrit.cardcrawl.actions.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.watcher.ChangeStanceAction;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.stances.AbstractStance;
import com.megacrit.cardcrawl.stances.CalmStance;
import com.megacrit.cardcrawl.stances.WrathStance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class DEPRECATEDRandomStanceAction
        extends AbstractGameAction {
    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {

            ArrayList<AbstractStance> stances = new ArrayList<>();
            AbstractStance oldStance = AbstractDungeon.player.stance;

            if (!oldStance.ID.equals("Wrath")) {
                stances.add(new WrathStance());
            }
            if (!oldStance.ID.equals("Calm")) {
                stances.add(new CalmStance());
            }

            Collections.shuffle(stances, new Random(AbstractDungeon.cardRandomRng.randomLong()));

            addToBot((AbstractGameAction) new ChangeStanceAction(((AbstractStance) stances.get(0)).ID));

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
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\deprecated
 * \DEPRECATEDRandomStanceAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




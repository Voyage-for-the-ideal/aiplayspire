package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.actions.defect.IncreaseMaxOrbAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class Inserter extends AbstractRelic {
    public static final String ID = "Inserter";

    public Inserter() {
        super("Inserter", "inserter.png", RelicTier.BOSS, LandingSound.SOLID);
    }
    private static final int NUM_TURNS = 2;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void onEquip() {
        this.counter = 0;
    }

    public void atTurnStart() {
        if (this.counter == -1) {
            this.counter += 2;
        } else {
            this.counter++;
        }

        if (this.counter == 2) {
            this.counter = 0;
            flash();
            addToBot(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
            addToBot((AbstractGameAction) new IncreaseMaxOrbAction(1));
        }
    }

    public AbstractRelic makeCopy() {
        return new Inserter();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Inserter.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


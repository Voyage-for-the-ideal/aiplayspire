package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class HornCleat extends AbstractRelic {
    public static final String ID = "HornCleat";

    public HornCleat() {
        super("HornCleat", "horn_cleat.png", RelicTier.UNCOMMON, LandingSound.HEAVY);
    }
    private static final int TURN_ACTIVATION = 2;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\016' + this.DESCRIPTIONS[1];
    }

    public void atBattleStart() {
        this.counter = 0;
    }

    public void atTurnStart() {
        if (!this.grayscale) {
            this.counter++;
        }
        if (this.counter == 2) {
            flash();
            addToBot(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
            addToBot((AbstractGameAction) new GainBlockAction((AbstractCreature) AbstractDungeon.player,
                    (AbstractCreature) AbstractDungeon.player, 14));
            this.counter = -1;
            this.grayscale = true;
        }
    }

    public void onVictory() {
        this.counter = -1;
        this.grayscale = false;
    }

    public AbstractRelic makeCopy() {
        return new HornCleat();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\HornCleat.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


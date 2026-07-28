package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class Abacus extends AbstractRelic {
    public static final String ID = "TheAbacus";

    public Abacus() {
        super("TheAbacus", "abacus.png", RelicTier.SHOP, LandingSound.SOLID);
    }
    private static final int BLOCK_AMT = 6;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\006' + this.DESCRIPTIONS[1];
    }

    public void onShuffle() {
        flash();
        addToBot((AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
        addToBot((AbstractGameAction) new GainBlockAction((AbstractCreature) AbstractDungeon.player,
                (AbstractCreature) AbstractDungeon.player, 6));
    }

    public AbstractRelic makeCopy() {
        return new Abacus();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Abacus.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


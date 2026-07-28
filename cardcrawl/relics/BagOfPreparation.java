package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class BagOfPreparation extends AbstractRelic {
    public static final String ID = "Bag of Preparation";

    public BagOfPreparation() {
        super("Bag of Preparation", "bag_of_prep.png", RelicTier.COMMON, LandingSound.FLAT);
    }
    private static final int NUM_CARDS = 2;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\002' + this.DESCRIPTIONS[1];
    }

    public void atBattleStart() {
        flash();
        addToBot((AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
        addToBot((AbstractGameAction) new DrawCardAction((AbstractCreature) AbstractDungeon.player, 2));
    }

    public AbstractRelic makeCopy() {
        return new BagOfPreparation();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * BagOfPreparation.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


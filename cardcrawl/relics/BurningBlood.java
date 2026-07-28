package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class BurningBlood extends AbstractRelic {
    public static final String ID = "Burning Blood";

    public BurningBlood() {
        super("Burning Blood", "burningBlood.png", RelicTier.STARTER, LandingSound.MAGICAL);
    }
    private static final int HEALTH_AMT = 6;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\006' + this.DESCRIPTIONS[1];
    }

    public void onVictory() {
        flash();
        addToTop((AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
        AbstractPlayer p = AbstractDungeon.player;
        if (p.currentHealth > 0) {
            p.heal(6);
        }
    }

    public AbstractRelic makeCopy() {
        return new BurningBlood();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * BurningBlood.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


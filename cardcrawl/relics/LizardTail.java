package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class LizardTail extends AbstractRelic {
    public LizardTail() {
        super("Lizard Tail", "lizardTail.png", RelicTier.RARE, LandingSound.MAGICAL);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }
    public static final String ID = "Lizard Tail";

    public void setCounter(int setCounter) {
        if (setCounter == -2) {
            usedUp();
            this.counter = -2;
        }
    }

    public void onTrigger() {
        flash();
        addToTop((AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
        int healAmt = AbstractDungeon.player.maxHealth / 2;

        if (healAmt < 1) {
            healAmt = 1;
        }

        AbstractDungeon.player.heal(healAmt, true);
        setCounter(-2);
    }

    public AbstractRelic makeCopy() {
        return new LizardTail();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\LizardTail.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class NlothsMask extends AbstractRelic {
    public static final String ID = "NlothsMask";

    public NlothsMask() {
        super("NlothsMask", "nloth.png", RelicTier.SPECIAL, LandingSound.FLAT);
        this.counter = 1;
    }

    public void onChestOpenAfter(boolean bossChest) {
        if (!bossChest &&
                this.counter > 0) {
            this.counter--;

            flash();
            AbstractDungeon.getCurrRoom().removeOneRelicFromRewards();

            if (this.counter == 0) {
                setCounter(-2);
            }
        }
    }

    public void setCounter(int setCounter) {
        this.counter = setCounter;
        if (setCounter == -2) {
            usedUp();
            this.counter = -2;
        }
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public AbstractRelic makeCopy() {
        return new NlothsMask();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\NlothsMask.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


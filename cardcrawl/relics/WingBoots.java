package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class WingBoots extends AbstractRelic {
    public static final String ID = "WingedGreaves";

    public WingBoots() {
        super("WingedGreaves", "winged.png", RelicTier.RARE, LandingSound.FLAT);
        this.counter = 3;
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void setCounter(int setCounter) {
        this.counter = setCounter;
        if (this.counter == -2) {
            usedUp();
            this.counter = -2;
        }
    }

    public boolean canSpawn() {
        return (Settings.isEndless || AbstractDungeon.floorNum <= 40);
    }

    public AbstractRelic makeCopy() {
        return new WingBoots();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\WingBoots.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


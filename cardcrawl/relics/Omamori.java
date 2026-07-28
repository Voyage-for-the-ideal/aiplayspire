package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class Omamori extends AbstractRelic {
    public static final String ID = "Omamori";

    public Omamori() {
        super("Omamori", "omamori.png", RelicTier.COMMON, LandingSound.FLAT);
        this.counter = 2;
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void setCounter(int setCounter) {
        this.counter = setCounter;
        if (setCounter == 0) {
            usedUp();
        } else if (setCounter == 1) {
            this.description = this.DESCRIPTIONS[1];
        }
    }

    public void use() {
        flash();
        this.counter--;
        if (this.counter == 0) {
            setCounter(0);
        } else {
            this.description = this.DESCRIPTIONS[1];
        }
    }

    public boolean canSpawn() {
        return (Settings.isEndless || AbstractDungeon.floorNum <= 48);
    }

    public AbstractRelic makeCopy() {
        return new Omamori();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Omamori.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


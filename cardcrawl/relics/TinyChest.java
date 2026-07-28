package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class TinyChest extends AbstractRelic {
    public static final String ID = "Tiny Chest";
    public static final int ROOM_COUNT = 4;

    public TinyChest() {
        super("Tiny Chest", "tinyChest.png", RelicTier.COMMON, LandingSound.SOLID);
        this.counter = -1;
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\004' + this.DESCRIPTIONS[1];
    }

    public void onEquip() {
        this.counter = 0;
    }

    public boolean canSpawn() {
        return (Settings.isEndless || AbstractDungeon.floorNum <= 35);
    }

    public AbstractRelic makeCopy() {
        return new TinyChest();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\TinyChest.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


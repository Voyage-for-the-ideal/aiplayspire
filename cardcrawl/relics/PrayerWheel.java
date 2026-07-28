package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class PrayerWheel extends AbstractRelic {
    public static final String ID = "Prayer Wheel";

    public PrayerWheel() {
        super("Prayer Wheel", "prayerWheel.png", RelicTier.RARE, LandingSound.CLINK);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public AbstractRelic makeCopy() {
        return new PrayerWheel();
    }

    public boolean canSpawn() {
        return (Settings.isEndless || AbstractDungeon.floorNum <= 48);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\PrayerWheel
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


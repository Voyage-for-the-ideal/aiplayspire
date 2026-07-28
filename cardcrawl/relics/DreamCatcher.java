package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class DreamCatcher extends AbstractRelic {
    public static final String ID = "Dream Catcher";

    public DreamCatcher() {
        super("Dream Catcher", "dreamCatcher.png", RelicTier.COMMON, LandingSound.MAGICAL);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public AbstractRelic makeCopy() {
        return new DreamCatcher();
    }

    public boolean canSpawn() {
        return (Settings.isEndless || AbstractDungeon.floorNum <= 48);
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * DreamCatcher.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


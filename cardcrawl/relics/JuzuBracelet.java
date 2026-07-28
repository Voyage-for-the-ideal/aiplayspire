package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class JuzuBracelet extends AbstractRelic {
    public static final String ID = "Juzu Bracelet";

    public JuzuBracelet() {
        super("Juzu Bracelet", "juzuBracelet.png", RelicTier.COMMON, LandingSound.MAGICAL);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public boolean canSpawn() {
        return (Settings.isEndless || AbstractDungeon.floorNum <= 48);
    }

    public AbstractRelic makeCopy() {
        return new JuzuBracelet();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * JuzuBracelet.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


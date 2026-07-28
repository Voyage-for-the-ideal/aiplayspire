package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.LocalizedStrings;

public class Pear
        extends AbstractRelic {
    public static final String ID = "Pear";
    private static final int HP_AMT = 10;

    public Pear() {
        super("Pear", "pear.png", RelicTier.UNCOMMON, LandingSound.FLAT);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\n' + LocalizedStrings.PERIOD;
    }

    public void onEquip() {
        AbstractDungeon.player.increaseMaxHp(10, true);
    }

    public AbstractRelic makeCopy() {
        return new Pear();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Pear.class
 * Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


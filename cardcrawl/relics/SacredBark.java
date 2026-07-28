package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.potions.AbstractPotion;

public class SacredBark extends AbstractRelic {
    public static final String ID = "SacredBark";

    public SacredBark() {
        super("SacredBark", "bark.png", RelicTier.BOSS, LandingSound.MAGICAL);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void onEquip() {
        for (AbstractPotion p : AbstractDungeon.player.potions) {
            p.initializeData();
        }
    }

    public AbstractRelic makeCopy() {
        return new SacredBark();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\SacredBark.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class Orrery extends AbstractRelic {
    public static final String ID = "Orrery";

    public Orrery() {
        super("Orrery", "orrery.png", RelicTier.SHOP, LandingSound.CLINK);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void onEquip() {
        for (int i = 0; i < 4; i++) {
            AbstractDungeon.getCurrRoom().addCardToRewards();
        }

        AbstractDungeon.combatRewardScreen.open(this.DESCRIPTIONS[1]);
        (AbstractDungeon.getCurrRoom()).rewardPopOutTimer = 0.0F;
    }

    public AbstractRelic makeCopy() {
        return new Orrery();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Orrery.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


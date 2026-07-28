package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class CeramicFish extends AbstractRelic {
    public static final String ID = "CeramicFish";
    private static final int GOLD_AMT = 9;

    public CeramicFish() {
        super("CeramicFish", "ceramic_fish.png", RelicTier.COMMON, LandingSound.FLAT);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\t' + this.DESCRIPTIONS[1];
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

    public void onObtainCard(AbstractCard c) {
        AbstractDungeon.player.gainGold(9);
    }

    public boolean canSpawn() {
        return (Settings.isEndless || AbstractDungeon.floorNum <= 48);
    }

    public AbstractRelic makeCopy() {
        return new CeramicFish();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\CeramicFish
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


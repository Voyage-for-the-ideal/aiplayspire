package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class MawBank
        extends AbstractRelic {
    public static final String ID = "MawBank";
    private static final int GOLD_AMT = 12;

    public MawBank() {
        super("MawBank", "bank.png", RelicTier.COMMON, LandingSound.CLINK);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\f' + this.DESCRIPTIONS[1];
    }

    public void onEnterRoom(AbstractRoom room) {
        if (!this.usedUp) {
            flash();
            AbstractDungeon.player.gainGold(12);
        }
    }

    public void onSpendGold() {
        if (!this.usedUp) {
            flash();
            setCounter(-2);
        }
    }

    public void setCounter(int setCounter) {
        this.counter = setCounter;
        if (setCounter == -2) {
            usedUp();
            this.counter = -2;
        }
    }

    public boolean canSpawn() {
        return ((Settings.isEndless || AbstractDungeon.floorNum <= 48) &&
                !(AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.ShopRoom));
    }

    public AbstractRelic makeCopy() {
        return new MawBank();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\MawBank.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


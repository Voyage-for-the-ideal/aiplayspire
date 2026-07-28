package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class Courier
        extends AbstractRelic {
    public static final String ID = "The Courier";
    public static final float MULTIPLIER = 0.8F;

    public Courier() {
        super("The Courier", "courier.png", RelicTier.UNCOMMON, LandingSound.FLAT);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void onEnterRoom(AbstractRoom room) {
        if (room instanceof com.megacrit.cardcrawl.rooms.ShopRoom) {
            flash();
            this.pulse = true;
        } else {
            this.pulse = false;
        }
    }

    public boolean canSpawn() {
        return ((Settings.isEndless || AbstractDungeon.floorNum <= 48) &&
                !(AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.ShopRoom));
    }

    public AbstractRelic makeCopy() {
        return new Courier();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Courier.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


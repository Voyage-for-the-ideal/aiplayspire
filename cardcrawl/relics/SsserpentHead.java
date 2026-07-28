package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class SsserpentHead
        extends AbstractRelic {
    public static final String ID = "SsserpentHead";
    private static final int GOLD_AMT = 50;

    public SsserpentHead() {
        super("SsserpentHead", "serpentHead.png", RelicTier.SPECIAL, LandingSound.CLINK);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '2' + this.DESCRIPTIONS[1];
    }

    public void onEnterRoom(AbstractRoom room) {
        if (room instanceof com.megacrit.cardcrawl.rooms.EventRoom) {
            flash();
            AbstractDungeon.player.gainGold(50);
        }
    }

    public AbstractRelic makeCopy() {
        return new SsserpentHead();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * SsserpentHead.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


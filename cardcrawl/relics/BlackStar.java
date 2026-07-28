package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class BlackStar
        extends AbstractRelic {
    public static final String ID = "Black Star";

    public BlackStar() {
        super("Black Star", "blackstar.png", RelicTier.BOSS, LandingSound.HEAVY);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void onEnterRoom(AbstractRoom room) {
        if (room instanceof com.megacrit.cardcrawl.rooms.MonsterRoomElite) {
            this.pulse = true;
            beginPulse();
        } else {
            this.pulse = false;
        }
    }

    public void onVictory() {
        if (AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.MonsterRoomElite) {
            flash();
            this.pulse = false;
        }
    }

    public AbstractRelic makeCopy() {
        return new BlackStar();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\BlackStar.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


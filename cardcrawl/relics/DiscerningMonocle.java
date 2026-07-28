package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class DiscerningMonocle
        extends AbstractRelic {
    public static final String ID = "Discerning Monocle";
    public static final float MULTIPLIER = 0.8F;

    public DiscerningMonocle() {
        super("Discerning Monocle", "monocle.png", RelicTier.UNCOMMON, LandingSound.CLINK);
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

    public AbstractRelic makeCopy() {
        return new DiscerningMonocle();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * DiscerningMonocle.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


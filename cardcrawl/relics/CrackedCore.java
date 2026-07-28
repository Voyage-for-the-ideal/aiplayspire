package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.orbs.Lightning;

public class CrackedCore
        extends AbstractRelic {
    public CrackedCore() {
        super("Cracked Core", "crackedOrb.png", RelicTier.STARTER, LandingSound.CLINK);
    }
    public static final String ID = "Cracked Core";

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void atPreBattle() {
        AbstractDungeon.player.channelOrb((AbstractOrb) new Lightning());
    }

    public AbstractRelic makeCopy() {
        return new CrackedCore();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\CrackedCore
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.orbs.Plasma;

public class NuclearBattery
        extends AbstractRelic {
    public NuclearBattery() {
        super("Nuclear Battery", "battery.png", RelicTier.BOSS, LandingSound.HEAVY);
    }
    public static final String ID = "Nuclear Battery";

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void atPreBattle() {
        AbstractDungeon.player.channelOrb((AbstractOrb) new Plasma());
    }

    public AbstractRelic makeCopy() {
        return new NuclearBattery();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * NuclearBattery.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


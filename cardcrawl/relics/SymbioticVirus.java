package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.orbs.Dark;

public class SymbioticVirus
        extends AbstractRelic {
    public SymbioticVirus() {
        super("Symbiotic Virus", "virus.png", RelicTier.UNCOMMON, LandingSound.MAGICAL);
    }
    public static final String ID = "Symbiotic Virus";

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void atPreBattle() {
        AbstractDungeon.player.channelOrb((AbstractOrb) new Dark());
    }

    public AbstractRelic makeCopy() {
        return new SymbioticVirus();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * SymbioticVirus.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


package com.megacrit.cardcrawl.relics;

public class WhiteBeast extends AbstractRelic {
    public static final String ID = "White Beast Statue";

    public WhiteBeast() {
        super("White Beast Statue", "whiteBeast.png", RelicTier.UNCOMMON, LandingSound.SOLID);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public AbstractRelic makeCopy() {
        return new WhiteBeast();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\WhiteBeast.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


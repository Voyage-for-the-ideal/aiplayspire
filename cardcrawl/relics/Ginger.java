package com.megacrit.cardcrawl.relics;

public class Ginger extends AbstractRelic {
    public static final String ID = "Ginger";

    public Ginger() {
        super("Ginger", "ginger.png", RelicTier.RARE, LandingSound.FLAT);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public AbstractRelic makeCopy() {
        return new Ginger();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Ginger.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


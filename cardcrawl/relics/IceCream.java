package com.megacrit.cardcrawl.relics;

public class IceCream extends AbstractRelic {
    public static final String ID = "Ice Cream";

    public IceCream() {
        super("Ice Cream", "iceCream.png", RelicTier.RARE, LandingSound.FLAT);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public AbstractRelic makeCopy() {
        return new IceCream();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\IceCream.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


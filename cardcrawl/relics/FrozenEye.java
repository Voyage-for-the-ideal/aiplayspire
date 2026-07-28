package com.megacrit.cardcrawl.relics;

public class FrozenEye extends AbstractRelic {
    public static final String ID = "Frozen Eye";

    public FrozenEye() {
        super("Frozen Eye", "frozenEye.png", RelicTier.SHOP, LandingSound.SOLID);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public AbstractRelic makeCopy() {
        return new FrozenEye();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\FrozenEye.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


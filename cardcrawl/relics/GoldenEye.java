package com.megacrit.cardcrawl.relics;

public class GoldenEye extends AbstractRelic {
    public static final String ID = "GoldenEye";

    public GoldenEye() {
        super("GoldenEye", "golden_eye.png", RelicTier.RARE, LandingSound.HEAVY);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\002' + this.DESCRIPTIONS[1];
    }

    public AbstractRelic makeCopy() {
        return new GoldenEye();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\GoldenEye.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


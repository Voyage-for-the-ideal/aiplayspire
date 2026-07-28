package com.megacrit.cardcrawl.relics;

public class PaperCrane
        extends AbstractRelic {
    public static final String ID = "Paper Crane";
    public static final float WEAK_EFFECTIVENESS = 0.6F;
    public static final int EFFECTIVENESS_STRING = 40;

    public PaperCrane() {
        super("Paper Crane", "paperCrane.png", RelicTier.UNCOMMON, LandingSound.FLAT);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public AbstractRelic makeCopy() {
        return new PaperCrane();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\PaperCrane.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


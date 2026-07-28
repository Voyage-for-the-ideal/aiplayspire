package com.megacrit.cardcrawl.relics;

public class SneckoSkull
        extends AbstractRelic {
    public static final String ID = "Snake Skull";
    public static final int EFFECT = 1;

    public SneckoSkull() {
        super("Snake Skull", "snakeSkull.png", RelicTier.COMMON, LandingSound.FLAT);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\001' + this.DESCRIPTIONS[1];
    }

    public AbstractRelic makeCopy() {
        return new SneckoSkull();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\SneckoSkull
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


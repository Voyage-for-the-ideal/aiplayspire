package com.megacrit.cardcrawl.relics;

public class TungstenRod extends AbstractRelic {
    public static final String ID = "TungstenRod";

    public TungstenRod() {
        super("TungstenRod", "tungsten.png", RelicTier.RARE, LandingSound.HEAVY);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\001' + this.DESCRIPTIONS[1];
    }

    public int onLoseHpLast(int damageAmount) {
        if (damageAmount > 0) {
            flash();
            return damageAmount - 1;
        }
        return damageAmount;
    }

    public AbstractRelic makeCopy() {
        return new TungstenRod();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\TungstenRod
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


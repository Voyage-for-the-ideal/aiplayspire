package com.megacrit.cardcrawl.relics;

public class Circlet extends AbstractRelic {
    public static final String ID = "Circlet";

    public Circlet() {
        super("Circlet", "circlet.png", RelicTier.SPECIAL, LandingSound.CLINK);
        this.counter = 1;
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void onEquip() {
        flash();
    }

    public void onUnequip() {
    }

    public AbstractRelic makeCopy() {
        return new Circlet();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Circlet.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


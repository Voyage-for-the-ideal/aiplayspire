package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.cards.AbstractCard;

public class WristBlade extends AbstractRelic {
    public static final String ID = "WristBlade";

    public WristBlade() {
        super("WristBlade", "wBlade.png", RelicTier.BOSS, LandingSound.FLAT);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public AbstractRelic makeCopy() {
        return new WristBlade();
    }

    public float atDamageModify(float damage, AbstractCard c) {
        if (c.costForTurn == 0 || (c.freeToPlayOnce && c.cost != -1)) {
            return damage + 4.0F;
        }
        return damage;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\WristBlade.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


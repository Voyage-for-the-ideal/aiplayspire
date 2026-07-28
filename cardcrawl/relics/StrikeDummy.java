package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.cards.AbstractCard;

public class StrikeDummy extends AbstractRelic {
    public static final String ID = "StrikeDummy";

    public StrikeDummy() {
        super("StrikeDummy", "dummy.png", RelicTier.UNCOMMON, LandingSound.HEAVY);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\003' + this.DESCRIPTIONS[1];
    }

    public float atDamageModify(float damage, AbstractCard c) {
        if (c.hasTag(AbstractCard.CardTags.STRIKE)) {
            return damage + 3.0F;
        }
        return damage;
    }

    public AbstractRelic makeCopy() {
        return new StrikeDummy();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\StrikeDummy
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


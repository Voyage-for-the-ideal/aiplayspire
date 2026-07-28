package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class MedicalKit extends AbstractRelic {
    public static final String ID = "Medical Kit";

    public MedicalKit() {
        super("Medical Kit", "medicalKit.png", RelicTier.SHOP, LandingSound.MAGICAL);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public AbstractRelic makeCopy() {
        return new MedicalKit();
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (card.type == AbstractCard.CardType.STATUS) {
            AbstractDungeon.player.getRelic("Medical Kit").flash();
            card.exhaust = true;
            action.exhaustCard = true;
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\MedicalKit.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


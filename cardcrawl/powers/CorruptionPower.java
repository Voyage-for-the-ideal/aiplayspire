package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class CorruptionPower
        extends AbstractPower {
    public static final String POWER_ID = "Corruption";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Corruption");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public CorruptionPower(AbstractCreature owner) {
        this.name = NAME;
        this.ID = "Corruption";
        this.owner = owner;
        this.amount = -1;
        this.description = DESCRIPTIONS[0];
        loadRegion("corruption");
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[1];
    }

    public void onCardDraw(AbstractCard card) {
        if (card.type == AbstractCard.CardType.SKILL) {
            card.setCostForTurn(-9);
        }
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (card.type == AbstractCard.CardType.SKILL) {
            flash();
            action.exhaustCard = true;
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * CorruptionPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


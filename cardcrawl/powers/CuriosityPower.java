package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class CuriosityPower extends AbstractPower {
    public static final String POWER_ID = "Curiosity";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Curiosity");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public CuriosityPower(AbstractCreature owner, int amount) {
        this.name = NAME;
        this.ID = "Curiosity";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("curiosity");
        this.type = PowerType.BUFF;
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (card.type == AbstractCard.CardType.POWER) {
            flash();
            addToBot((AbstractGameAction) new ApplyPowerAction(this.owner, this.owner,
                    new StrengthPower(this.owner, this.amount), this.amount));
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * CuriosityPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


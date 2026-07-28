package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInHandAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class NightmarePower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Night Terror");
    public static final String POWER_ID = "Night Terror";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;
    private AbstractCard card;

    public NightmarePower(AbstractCreature owner, int cardAmt, AbstractCard copyMe) {
        this.name = NAME;
        this.ID = "Night Terror";
        this.owner = owner;
        this.amount = cardAmt;
        loadRegion("nightmare");
        this.card = copyMe.makeStatEquivalentCopy();
        this.card.resetAttributes();
        updateDescription();
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + " " + FontHelper.colorString(this.card.name, "y")
                + DESCRIPTIONS[1];
    }

    public void atStartOfTurn() {
        addToBot((AbstractGameAction) new MakeTempCardInHandAction(this.card, this.amount));
        addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "Night Terror"));
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * NightmarePower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


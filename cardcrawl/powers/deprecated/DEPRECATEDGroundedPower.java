package com.megacrit.cardcrawl.powers.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.actions.watcher.ChangeStanceAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class DEPRECATEDGroundedPower
        extends AbstractPower {
    public static final String POWER_ID = "Grounded";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Grounded");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public DEPRECATEDGroundedPower(AbstractCreature owner) {
        this.name = NAME;
        this.ID = "Grounded";
        this.owner = owner;
        this.amount = -1;
        updateDescription();
        loadRegion("corruption");
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0];
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (card.type == AbstractCard.CardType.SKILL) {
            flash();
            addToBot((AbstractGameAction) new ChangeStanceAction("Calm"));
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\deprecated\
 * DEPRECATEDGroundedPower.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */


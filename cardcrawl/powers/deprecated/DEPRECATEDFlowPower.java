package com.megacrit.cardcrawl.powers.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.*;

public class DEPRECATEDFlowPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("FlowPower");
    public static final String POWER_ID = "FlowPower";

    public DEPRECATEDFlowPower(AbstractCreature owner, int amount) {
        this.name = powerStrings.NAME;
        this.ID = "FlowPower";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("afterImage");
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1] + this.amount
                + powerStrings.DESCRIPTIONS[2];
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (card.type == AbstractCard.CardType.SKILL) {
            flash();
            addToBot((AbstractGameAction) new ApplyPowerAction(this.owner, this.owner,
                    (AbstractPower) new StrengthPower(this.owner, this.amount), this.amount));
            addToBot((AbstractGameAction) new ApplyPowerAction(this.owner, this.owner,
                    (AbstractPower) new LoseStrengthPower(this.owner, this.amount), this.amount));
        } else if (card.type == AbstractCard.CardType.ATTACK) {
            flash();
            addToBot((AbstractGameAction) new ApplyPowerAction(this.owner, this.owner,
                    (AbstractPower) new DexterityPower(this.owner, this.amount), this.amount));
            addToBot((AbstractGameAction) new ApplyPowerAction(this.owner, this.owner,
                    (AbstractPower) new LoseDexterityPower(this.owner, this.amount), this.amount));
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\deprecated\
 * DEPRECATEDFlowPower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


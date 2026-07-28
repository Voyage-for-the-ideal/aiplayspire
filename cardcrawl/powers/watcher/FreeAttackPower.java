package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class FreeAttackPower extends AbstractPower {
    public static final String POWER_ID = "FreeAttackPower";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("FreeAttackPower");

    public FreeAttackPower(AbstractCreature owner, int amount) {
        this.name = powerStrings.NAME;
        this.ID = "FreeAttackPower";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("swivel");
    }

    public void updateDescription() {
        if (this.amount == 1) {
            this.description = powerStrings.DESCRIPTIONS[0];
        } else {
            this.description = powerStrings.DESCRIPTIONS[1] + this.amount + powerStrings.DESCRIPTIONS[2];
        }
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (card.type == AbstractCard.CardType.ATTACK && !card.purgeOnUse && this.amount > 0) {
            flash();
            this.amount--;
            if (this.amount == 0)
                addToTop((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "FreeAttackPower"));
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * FreeAttackPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


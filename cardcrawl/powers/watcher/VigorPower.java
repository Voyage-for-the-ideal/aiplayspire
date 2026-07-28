package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class VigorPower extends AbstractPower {
    public static final String POWER_ID = "Vigor";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Vigor");

    public VigorPower(AbstractCreature owner, int amount) {
        this.name = powerStrings.NAME;
        this.ID = "Vigor";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("vigor");
        this.type = PowerType.BUFF;
        this.isTurnBased = false;
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
    }

    public float atDamageGive(float damage, DamageInfo.DamageType type) {
        if (type == DamageInfo.DamageType.NORMAL) {
            return damage += this.amount;
        }
        return damage;
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (card.type == AbstractCard.CardType.ATTACK) {

            flash();
            addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "Vigor"));
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * VigorPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class DoubleDamagePower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Double Damage");
    public static final String POWER_ID = "Double Damage";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    private boolean justApplied = false;

    public DoubleDamagePower(AbstractCreature owner, int amount, boolean isSourceMonster) {
        this.name = NAME;
        this.ID = "Double Damage";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("doubleDamage");
        this.justApplied = isSourceMonster;
        this.type = PowerType.BUFF;
        this.isTurnBased = true;
        this.priority = 6;
    }

    public void atEndOfRound() {
        if (this.justApplied) {
            this.justApplied = false;

            return;
        }
        if (this.amount == 0) {
            addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "Double Damage"));
        } else {
            addToBot((AbstractGameAction) new ReducePowerAction(this.owner, this.owner, "Double Damage", 1));
        }
    }

    public void updateDescription() {
        if (this.amount == 1) {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
        } else {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[2];
        }
    }

    public float atDamageGive(float damage, DamageInfo.DamageType type) {
        if (type == DamageInfo.DamageType.NORMAL) {
            return damage * 2.0F;
        }
        return damage;
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * DoubleDamagePower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


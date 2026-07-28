package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class WeakPower extends AbstractPower {
    public static final String POWER_ID = "Weakened";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Weakened");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;
    private boolean justApplied = false;
    private static final int EFFECTIVENESS_STRING = 25;

    public WeakPower(AbstractCreature owner, int amount, boolean isSourceMonster) {
        this.name = NAME;
        this.ID = "Weakened";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("weak");

        if (isSourceMonster) {
            this.justApplied = true;
        }

        this.type = PowerType.DEBUFF;
        this.isTurnBased = true;

        this.priority = 99;
    }

    public void atEndOfRound() {
        if (this.justApplied) {
            this.justApplied = false;

            return;
        }
        if (this.amount == 0) {
            addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "Weakened"));
        } else {
            addToBot((AbstractGameAction) new ReducePowerAction(this.owner, this.owner, "Weakened", 1));
        }
    }

    public void updateDescription() {
        if (this.amount == 1) {
            if (this.owner != null && !this.owner.isPlayer && AbstractDungeon.player.hasRelic("Paper Crane")) {
                this.description = DESCRIPTIONS[0] + '(' + DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[2];
            } else {

                this.description = DESCRIPTIONS[0] + '\031' + DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[2];
            }

        } else if (this.owner != null && !this.owner.isPlayer && AbstractDungeon.player.hasRelic("Paper Crane")) {
            this.description = DESCRIPTIONS[0] + '(' + DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[3];
        } else {

            this.description = DESCRIPTIONS[0] + '\031' + DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[3];
        }
    }

    public float atDamageGive(float damage, DamageInfo.DamageType type) {
        if (type == DamageInfo.DamageType.NORMAL) {
            if (!this.owner.isPlayer && AbstractDungeon.player.hasRelic("Paper Crane")) {
                return damage * 0.6F;
            }
            return damage * 0.75F;
        }
        return damage;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\WeakPower.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


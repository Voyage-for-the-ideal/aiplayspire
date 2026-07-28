package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class InvinciblePower extends AbstractPower {
    public static final String POWER_ID = "Invincible";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Invincible");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;
    private int maxAmt;

    public InvinciblePower(AbstractCreature owner, int amount) {
        this.name = NAME;
        this.ID = "Invincible";
        this.owner = owner;
        this.amount = amount;
        this.maxAmt = amount;
        updateDescription();
        loadRegion("heartDef");
        this.priority = 99;
    }

    public int onAttackedToChangeDamage(DamageInfo info, int damageAmount) {
        if (damageAmount > this.amount) {
            damageAmount = this.amount;
        }
        this.amount -= damageAmount;
        if (this.amount < 0) {
            this.amount = 0;
        }

        updateDescription();
        return damageAmount;
    }

    public void atStartOfTurn() {
        this.amount = this.maxAmt;
        updateDescription();
    }

    public void updateDescription() {
        if (this.amount <= 0) {
            this.description = DESCRIPTIONS[2];
        } else {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * InvinciblePower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class SlowPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Slow");
    public static final String POWER_ID = "Slow";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public SlowPower(AbstractCreature owner, int amount) {
        this.name = NAME;
        this.ID = "Slow";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("slow");
        this.type = PowerType.DEBUFF;
    }

    public void atEndOfRound() {
        this.amount = 0;
        updateDescription();
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + FontHelper.colorString(this.owner.name, "y") + DESCRIPTIONS[1];

        if (this.amount != 0) {
            this.description += DESCRIPTIONS[2] + (this.amount * 10) + DESCRIPTIONS[3];
        }
    }

    public void onAfterUseCard(AbstractCard card, UseCardAction action) {
        addToBot((AbstractGameAction) new ApplyPowerAction(this.owner, this.owner, new SlowPower(this.owner, 1), 1));
    }

    public float atDamageReceive(float damage, DamageInfo.DamageType type) {
        if (type == DamageInfo.DamageType.NORMAL) {
            return damage * (1.0F + this.amount * 0.1F);
        }
        return damage;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\SlowPower.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


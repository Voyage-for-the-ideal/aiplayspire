package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class IntangiblePlayerPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("IntangiblePlayer");
    public static final String POWER_ID = "IntangiblePlayer";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public IntangiblePlayerPower(AbstractCreature owner, int turns) {
        this.name = NAME;
        this.ID = "IntangiblePlayer";
        this.owner = owner;
        this.amount = turns;
        updateDescription();
        loadRegion("intangible");
        this.priority = 75;
    }

    public void playApplyPowerSfx() {
        CardCrawlGame.sound.play("POWER_INTANGIBLE", 0.05F);
    }

    public float atDamageFinalReceive(float damage, DamageInfo.DamageType type) {
        if (damage > 1.0F) {
            damage = 1.0F;
        }
        return damage;
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0];
    }

    public void atEndOfRound() {
        flash();

        if (this.amount == 0) {
            addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "IntangiblePlayer"));
        } else {
            addToBot((AbstractGameAction) new ReducePowerAction(this.owner, this.owner, "IntangiblePlayer", 1));
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * IntangiblePlayerPower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


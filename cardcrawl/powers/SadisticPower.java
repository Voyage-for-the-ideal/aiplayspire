package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class SadisticPower
        extends AbstractPower {
    public static final String POWER_ID = "Sadistic";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Sadistic");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public SadisticPower(AbstractCreature owner, int amount) {
        this.name = NAME;
        this.ID = "Sadistic";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("sadistic");
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
    }

    public void onApplyPower(AbstractPower power, AbstractCreature target, AbstractCreature source) {
        if (power.type == PowerType.DEBUFF && !power.ID.equals("Shackled") && source == this.owner
                && target != this.owner &&
                !target.hasPower("Artifact")) {
            flash();
            addToBot((AbstractGameAction) new DamageAction(target,
                    new DamageInfo(this.owner, this.amount, DamageInfo.DamageType.THORNS),
                    AbstractGameAction.AttackEffect.FIRE));
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * SadisticPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


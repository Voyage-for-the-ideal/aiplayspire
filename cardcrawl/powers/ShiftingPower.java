package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class ShiftingPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Shifting");
    public static final String POWER_ID = "Shifting";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public ShiftingPower(AbstractCreature owner) {
        this.name = NAME;
        this.ID = "Shifting";
        this.owner = owner;
        updateDescription();
        this.isPostActionPower = true;
        loadRegion("shift");
    }

    public int onAttacked(DamageInfo info, int damageAmount) {
        if (damageAmount > 0) {
            addToTop((AbstractGameAction) new ApplyPowerAction(this.owner, this.owner,
                    new StrengthPower(this.owner, -damageAmount), -damageAmount));

            if (!this.owner.hasPower("Artifact")) {
                addToTop((AbstractGameAction) new ApplyPowerAction(this.owner, this.owner,
                        new GainStrengthPower(this.owner, damageAmount), damageAmount));
            }
            flash();
        }

        return damageAmount;
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[1];
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * ShiftingPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


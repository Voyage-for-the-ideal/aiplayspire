package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class RupturePower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Rupture");
    public static final String POWER_ID = "Rupture";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public RupturePower(AbstractCreature owner, int strAmt) {
        this.name = NAME;
        this.ID = "Rupture";
        this.owner = owner;
        this.amount = strAmt;
        updateDescription();
        this.isPostActionPower = true;
        loadRegion("rupture");
    }

    public void wasHPLost(DamageInfo info, int damageAmount) {
        if (damageAmount > 0 && info.owner == this.owner) {
            flash();
            addToTop((AbstractGameAction) new ApplyPowerAction(this.owner, this.owner,
                    new StrengthPower(this.owner, this.amount), this.amount));
        }
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * RupturePower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


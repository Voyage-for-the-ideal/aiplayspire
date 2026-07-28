package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class AngryPower extends AbstractPower {
    public static final String POWER_ID = "Angry";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Angry");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public AngryPower(AbstractCreature owner, int attackAmount) {
        this.name = NAME;
        this.ID = "Angry";
        this.owner = owner;
        this.amount = attackAmount;
        updateDescription();
        this.isPostActionPower = true;
        loadRegion("anger");
    }

    public int onAttacked(DamageInfo info, int damageAmount) {
        if (info.owner != null && damageAmount > 0 && info.type != DamageInfo.DamageType.HP_LOSS
                && info.type != DamageInfo.DamageType.THORNS) {

            addToTop((AbstractGameAction) new ApplyPowerAction(this.owner, this.owner,
                    new StrengthPower(this.owner, this.amount), this.amount));
            flash();
        }
        return damageAmount;
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[2];
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\AngryPower.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


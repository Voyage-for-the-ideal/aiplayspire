package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInDiscardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.cards.status.Wound;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class PainfulStabsPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Painful Stabs");
    public static final String POWER_ID = "Painful Stabs";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public PainfulStabsPower(AbstractCreature owner) {
        this.name = NAME;
        this.ID = "Painful Stabs";
        this.owner = owner;
        this.amount = -1;
        updateDescription();
        loadRegion("painfulStabs");
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0];
    }

    public void onInflictDamage(DamageInfo info, int damageAmount, AbstractCreature target) {
        if (damageAmount > 0 && info.type != DamageInfo.DamageType.THORNS)
            addToBot((AbstractGameAction) new MakeTempCardInDiscardAction((AbstractCard) new Wound(), 1));
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * PainfulStabsPower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


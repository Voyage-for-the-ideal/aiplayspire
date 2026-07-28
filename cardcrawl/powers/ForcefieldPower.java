package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class ForcefieldPower extends AbstractPower {
    public static final String POWER_ID = "Nullify Attack";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Nullify Attack");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public ForcefieldPower(AbstractCreature owner) {
        this.name = NAME;
        this.ID = "Nullify Attack";
        this.owner = owner;
        this.amount = -1;
        updateDescription();
        loadRegion("forcefield");
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0];
    }

    public float atDamageFinalReceive(float damage, DamageInfo.DamageType type) {
        if (damage > 0.0F && type != DamageInfo.DamageType.HP_LOSS && type != DamageInfo.DamageType.THORNS) {
            return 0.0F;
        }
        return damage;
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * ForcefieldPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


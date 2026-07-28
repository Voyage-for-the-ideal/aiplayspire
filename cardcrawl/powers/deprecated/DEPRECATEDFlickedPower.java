package com.megacrit.cardcrawl.powers.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class DEPRECATEDFlickedPower
        extends AbstractPower {
    public static final String POWER_ID = "FlickPower";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("FlickPower");
    private static final int FLICK_DMG = 50;

    public DEPRECATEDFlickedPower(AbstractCreature owner, int amt) {
        this.name = powerStrings.NAME;
        this.ID = "FlickPower";
        this.owner = owner;
        this.amount = amt;
        updateDescription();
        loadRegion("talk_to_hand");
        this.type = PowerType.DEBUFF;
    }

    public void stackPower(int stackAmount) {
        this.amount += stackAmount;
        if (this.amount >= 3) {
            addToBot((AbstractGameAction) new DamageAction(this.owner,
                    new DamageInfo(null, 50, DamageInfo.DamageType.THORNS), AbstractGameAction.AttackEffect.BLUNT_HEAVY,
                    true));

            addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, null, "FlickPower"));
        } else {
            this.fontScale = 8.0F;
            updateDescription();
        }
    }

    public void updateDescription() {
        if (this.amount == 1) {
            this.description = powerStrings.DESCRIPTIONS[0] + powerStrings.DESCRIPTIONS[1] + '2'
                    + powerStrings.DESCRIPTIONS[3];
        } else {

            this.description = powerStrings.DESCRIPTIONS[0] + powerStrings.DESCRIPTIONS[2] + '2'
                    + powerStrings.DESCRIPTIONS[3];
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\deprecated\
 * DEPRECATEDFlickedPower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


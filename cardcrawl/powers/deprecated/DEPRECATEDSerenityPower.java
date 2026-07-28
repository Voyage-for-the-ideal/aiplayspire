package com.megacrit.cardcrawl.powers.deprecated;

import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.LocalizedStrings;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class DEPRECATEDSerenityPower
        extends AbstractPower {
    public static final String POWER_ID = "Serenity";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Serenity");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public DEPRECATEDSerenityPower(AbstractCreature owner, int amt) {
        this.name = NAME;
        this.ID = "Serenity";
        this.owner = owner;
        this.amount = amt;
        updateDescription();
        loadRegion("platedarmor");
    }

    public void playApplyPowerSfx() {
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + LocalizedStrings.PERIOD;
    }

    public int onAttacked(DamageInfo info, int damageAmount) {
        if (damageAmount > 0 && ((AbstractPlayer) this.owner).stance.ID.equals("Calm")) {
            flash();
            damageAmount -= this.amount;
            if (damageAmount < this.amount) {
                damageAmount = 0;
            }
        }
        return damageAmount;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\deprecated\
 * DEPRECATEDSerenityPower.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */


package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class BackAttackPower extends AbstractPower {
    public static final String POWER_ID = "BackAttack";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("BackAttack");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public BackAttackPower(AbstractCreature owner) {
        this.name = NAME;
        this.type = PowerType.BUFF;
        this.ID = "BackAttack";
        this.owner = owner;
        this.amount = -1;
        updateDescription();

        if (owner.hb.cX < Settings.WIDTH / 2.0F) {
            loadRegion("backAttack");
        } else {
            loadRegion("backAttack2");
        }
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0];
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * BackAttackPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


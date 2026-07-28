package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.HealAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class RegenerateMonsterPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Regenerate");
    public static final String POWER_ID = "Regenerate";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public RegenerateMonsterPower(AbstractMonster owner, int regenAmt) {
        this.name = NAME;
        this.ID = "Regenerate";
        this.owner = (AbstractCreature) owner;
        this.amount = regenAmt;
        updateDescription();
        loadRegion("regen");
        this.type = PowerType.BUFF;
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
    }

    public void atEndOfTurn(boolean isPlayer) {
        flash();
        if (!this.owner.halfDead && !this.owner.isDying && !this.owner.isDead)
            addToBot((AbstractGameAction) new HealAction(this.owner, this.owner, this.amount));
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * RegenerateMonsterPower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


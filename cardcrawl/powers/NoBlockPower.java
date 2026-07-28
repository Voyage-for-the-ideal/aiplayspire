package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class NoBlockPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("NoBlockPower");
    public static final String POWER_ID = "NoBlockPower";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;
    private boolean justApplied = false;

    public NoBlockPower(AbstractCreature owner, int amount, boolean isSourceMonster) {
        this.name = NAME;
        this.ID = "NoBlockPower";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("noBlock");

        if (AbstractDungeon.actionManager.turnHasEnded && isSourceMonster) {
            this.justApplied = true;
        }

        this.type = PowerType.DEBUFF;
        this.isTurnBased = true;
    }

    public void atEndOfRound() {
        if (this.justApplied) {
            this.justApplied = false;

            return;
        }
        if (this.amount == 0) {
            addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "NoBlockPower"));
        } else {
            addToBot((AbstractGameAction) new ReducePowerAction(this.owner, this.owner, "NoBlockPower", 1));
        }
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0];
    }

    public float modifyBlockLast(float blockAmount) {
        return 0.0F;
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * NoBlockPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


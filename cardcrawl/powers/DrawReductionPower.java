package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class DrawReductionPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Draw Reduction");
    public static final String POWER_ID = "Draw Reduction";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    private boolean justApplied = true;

    public DrawReductionPower(AbstractCreature owner, int amount) {
        this.name = NAME;
        this.ID = "Draw Reduction";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("lessdraw");
        this.type = PowerType.DEBUFF;
        this.isTurnBased = true;
    }

    public void onInitialApplication() {
        AbstractDungeon.player.gameHandSize--;
    }

    public void atEndOfRound() {
        if (this.justApplied) {
            this.justApplied = false;

            return;
        }
        addToBot((AbstractGameAction) new ReducePowerAction(this.owner, this.owner, "Draw Reduction", 1));
    }

    public void onRemove() {
        AbstractDungeon.player.gameHandSize++;
    }

    public void updateDescription() {
        if (this.amount == 1) {
            this.description = DESCRIPTIONS[0];
        } else {
            this.description = DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[2];
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * DrawReductionPower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DiscardAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class ToolsOfTheTradePower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Tools Of The Trade");
    public static final String POWER_ID = "Tools Of The Trade";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public ToolsOfTheTradePower(AbstractCreature owner, int drawAmount) {
        this.name = NAME;
        this.ID = "Tools Of The Trade";
        this.owner = owner;
        this.amount = drawAmount;
        updateDescription();
        loadRegion("tools");
        this.priority = 25;
    }

    public void updateDescription() {
        if (this.amount == 1) {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[2];
        } else {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[3] + this.amount + DESCRIPTIONS[4];
        }
    }

    public void atStartOfTurnPostDraw() {
        flash();
        addToBot((AbstractGameAction) new DrawCardAction(this.owner, this.amount));
        addToBot((AbstractGameAction) new DiscardAction(this.owner, this.owner, this.amount, false));
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * ToolsOfTheTradePower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


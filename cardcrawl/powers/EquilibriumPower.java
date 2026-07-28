package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class EquilibriumPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Equilibrium");
    public static final String POWER_ID = "Equilibrium";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public EquilibriumPower(AbstractCreature owner, int amount) {
        this.name = NAME;
        this.ID = "Equilibrium";
        this.owner = owner;
        this.amount = amount;
        this.description = DESCRIPTIONS[0];
        loadRegion("retain");
        this.isTurnBased = true;
    }

    public void updateDescription() {
        if (this.amount == 1) {
            this.description = DESCRIPTIONS[0];
        } else {
            this.description = DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[2];
        }
    }

    public void atEndOfTurn(boolean isPlayer) {
        if (isPlayer) {
            for (AbstractCard c : AbstractDungeon.player.hand.group) {
                if (!c.isEthereal) {
                    c.retain = true;
                }
            }
        }
    }

    public void atEndOfRound() {
        if (this.amount == 0) {
            addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "Equilibrium"));
        } else {
            addToBot((AbstractGameAction) new ReducePowerAction(this.owner, this.owner, "Equilibrium", 1));
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * EquilibriumPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


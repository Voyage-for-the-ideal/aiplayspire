package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class DrawPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Draw");
    public static final String POWER_ID = "Draw";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public DrawPower(AbstractCreature owner, int amount) {
        this.name = NAME;
        this.ID = "Draw";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("draw");

        if (amount < 0) {
            this.type = PowerType.DEBUFF;
            loadRegion("draw2");
        } else {
            this.type = PowerType.BUFF;
            loadRegion("draw");
        }

        this.isTurnBased = false;
        AbstractDungeon.player.gameHandSize += amount;
    }

    public void onRemove() {
        AbstractDungeon.player.gameHandSize -= this.amount;
    }

    public void reducePower(int reduceAmount) {
        this.fontScale = 8.0F;
        this.amount -= reduceAmount;

        if (this.amount == 0) {
            addToTop((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "Draw"));
        }
    }

    public void updateDescription() {
        if (this.amount > 0) {
            if (this.amount == 1) {
                this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
            } else {
                this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[3];
            }
            this.type = PowerType.BUFF;
        } else {
            if (this.amount == -1) {
                this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[2];
            } else {
                this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[4];
            }
            this.type = PowerType.DEBUFF;
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\DrawPower.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


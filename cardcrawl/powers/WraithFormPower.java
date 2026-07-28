package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class WraithFormPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Wraith Form v2");
    public static final String POWER_ID = "Wraith Form v2";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public WraithFormPower(AbstractCreature owner, int amount) {
        this.name = NAME;
        this.ID = "Wraith Form v2";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("wraithForm");
        this.canGoNegative = true;
        this.type = PowerType.DEBUFF;
    }

    public void atEndOfTurn(boolean isPlayer) {
        addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) AbstractDungeon.player,
                (AbstractCreature) AbstractDungeon.player,
                new DexterityPower((AbstractCreature) AbstractDungeon.player, this.amount), this.amount));
    }

    public void stackPower(int stackAmount) {
        this.fontScale = 8.0F;
        this.amount += stackAmount;
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + -this.amount + DESCRIPTIONS[1];
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * WraithFormPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


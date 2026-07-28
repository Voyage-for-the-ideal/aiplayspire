package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class PanachePower
        extends AbstractPower {
    public static final String POWER_ID = "Panache";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Panache");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;
    public static final int CARD_AMT = 5;
    private int damage;

    public PanachePower(AbstractCreature owner, int damage) {
        this.name = NAME;
        this.ID = "Panache";
        this.owner = owner;
        this.amount = 5;
        this.damage = damage;
        updateDescription();
        loadRegion("panache");
    }

    public void updateDescription() {
        if (this.amount == 1) {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1] + this.damage + DESCRIPTIONS[2];
        } else {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[3] + this.damage + DESCRIPTIONS[2];
        }
    }

    public void stackPower(int stackAmount) {
        this.fontScale = 8.0F;
        this.damage += stackAmount;
        updateDescription();
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        this.amount--;
        if (this.amount == 0) {
            flash();
            this.amount = 5;
            addToBot((AbstractGameAction) new DamageAllEnemiesAction((AbstractCreature) AbstractDungeon.player,

                    DamageInfo.createDamageMatrix(this.damage, true), DamageInfo.DamageType.THORNS,
                    AbstractGameAction.AttackEffect.SLASH_DIAGONAL));
        }

        updateDescription();
    }

    public void atStartOfTurn() {
        this.amount = 5;
        updateDescription();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * PanachePower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


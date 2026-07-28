package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class SharpHidePower
        extends AbstractPower {
    public static final String POWER_ID = "Sharp Hide";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Sharp Hide");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public SharpHidePower(AbstractCreature owner, int amount) {
        this.name = NAME;
        this.ID = "Sharp Hide";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("sharpHide");
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (card.type == AbstractCard.CardType.ATTACK) {
            flash();
            addToBot((AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player,
                    new DamageInfo(this.owner, this.amount, DamageInfo.DamageType.THORNS),
                    AbstractGameAction.AttackEffect.SLASH_HORIZONTAL));
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * SharpHidePower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


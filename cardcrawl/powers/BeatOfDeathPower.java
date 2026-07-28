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

public class BeatOfDeathPower extends AbstractPower {
    public static final String POWER_ID = "BeatOfDeath";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("BeatOfDeath");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public BeatOfDeathPower(AbstractCreature owner, int amount) {
        this.name = NAME;
        this.ID = "BeatOfDeath";
        this.owner = owner;
        this.amount = amount;
        this.description = DESCRIPTIONS[0] + amount + DESCRIPTIONS[1];
        loadRegion("beat");
        this.type = PowerType.BUFF;
    }

    public void onAfterUseCard(AbstractCard card, UseCardAction action) {
        flash();
        addToBot((AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player,
                new DamageInfo(this.owner, this.amount, DamageInfo.DamageType.THORNS),
                AbstractGameAction.AttackEffect.BLUNT_LIGHT));

        updateDescription();
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * BeatOfDeathPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


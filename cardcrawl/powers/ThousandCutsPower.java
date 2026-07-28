package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.CleaveEffect;

public class ThousandCutsPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Thousand Cuts");
    public static final String POWER_ID = "Thousand Cuts";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public ThousandCutsPower(AbstractCreature owner, int amount) {
        this.name = NAME;
        this.ID = "Thousand Cuts";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("thousandCuts");
    }

    public void onAfterCardPlayed(AbstractCard card) {
        flash();
        addToBot((AbstractGameAction) new SFXAction("ATTACK_HEAVY"));
        if (Settings.FAST_MODE) {
            addToBot((AbstractGameAction) new VFXAction((AbstractGameEffect) new CleaveEffect()));
        } else {
            addToBot((AbstractGameAction) new VFXAction(this.owner, (AbstractGameEffect) new CleaveEffect(), 0.2F));
        }
        addToBot((AbstractGameAction) new DamageAllEnemiesAction(this.owner,

                DamageInfo.createDamageMatrix(this.amount, true), DamageInfo.DamageType.THORNS,
                AbstractGameAction.AttackEffect.NONE, true));
    }

    public void stackPower(int stackAmount) {
        this.fontScale = 8.0F;
        this.amount += stackAmount;
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * ThousandCutsPower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


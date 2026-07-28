package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class VaultPower
        extends AbstractPower {
    public static final String POWER_ID = "Vault";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Vault");
    private AbstractCreature source;

    public VaultPower(AbstractCreature target, AbstractCreature source, int amount) {
        this.name = powerStrings.NAME;
        this.ID = "Vault";
        this.owner = target;
        this.source = source;
        this.amount = amount;
        updateDescription();
        loadRegion("carddraw");
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
    }

    public void atEndOfRound() {
        flash();
        addToBot((AbstractGameAction) new DamageAction(this.owner,
                new DamageInfo(this.source, this.amount, DamageInfo.DamageType.NORMAL),
                AbstractGameAction.AttackEffect.BLUNT_HEAVY));
        addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "Vault"));
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * VaultPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


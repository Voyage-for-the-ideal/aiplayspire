package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.LoseHPAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.LightningEffect;

public class EndTurnDeathPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("EndTurnDeath");
    public static final String POWER_ID = "EndTurnDeath";

    public EndTurnDeathPower(AbstractCreature owner) {
        this.name = powerStrings.NAME;
        this.ID = "EndTurnDeath";
        this.owner = owner;
        this.amount = -1;
        updateDescription();
        loadRegion("end_turn_death");
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0];
    }

    public void atStartOfTurn() {
        flash();
        addToBot((AbstractGameAction) new VFXAction(
                (AbstractGameEffect) new LightningEffect(this.owner.hb.cX, this.owner.hb.cY)));
        addToBot((AbstractGameAction) new LoseHPAction(this.owner, this.owner, 99999));
        addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "EndTurnDeath"));
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * EndTurnDeathPower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


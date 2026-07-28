package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class EntanglePower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Entangled");
    public static final String POWER_ID = "Entangled";

    public EntanglePower(AbstractCreature owner) {
        this.name = powerStrings.NAME;
        this.ID = "Entangled";
        this.owner = owner;
        this.amount = 1;
        updateDescription();
        loadRegion("entangle");
        this.isTurnBased = true;
        this.type = PowerType.DEBUFF;
    }

    public void playApplyPowerSfx() {
        CardCrawlGame.sound.play("POWER_ENTANGLED", 0.05F);
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0];
    }

    public void atEndOfTurn(boolean isPlayer) {
        if (isPlayer)
            addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "Entangled"));
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * EntanglePower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


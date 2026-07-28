package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class ArtifactPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Artifact");
    public static final String POWER_ID = "Artifact";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public ArtifactPower(AbstractCreature owner, int amount) {
        this.name = NAME;
        this.ID = "Artifact";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("artifact");
        this.type = PowerType.BUFF;
    }

    public void onSpecificTrigger() {
        if (this.amount <= 0) {
            addToTop((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "Artifact"));
        } else {
            addToTop((AbstractGameAction) new ReducePowerAction(this.owner, this.owner, "Artifact", 1));
        }
    }

    public void updateDescription() {
        if (this.amount == 1) {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
        } else {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[2];
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * ArtifactPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


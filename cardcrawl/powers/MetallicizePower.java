package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class MetallicizePower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Metallicize");
    public static final String POWER_ID = "Metallicize";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public MetallicizePower(AbstractCreature owner, int armorAmt) {
        this.name = NAME;
        this.ID = "Metallicize";
        this.owner = owner;
        this.amount = armorAmt;
        updateDescription();
        loadRegion("armor");
    }

    public void playApplyPowerSfx() {
        CardCrawlGame.sound.play("POWER_METALLICIZE", 0.05F);
    }

    public void updateDescription() {
        if (this.owner.isPlayer) {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
        } else {
            this.description = DESCRIPTIONS[2] + this.amount + DESCRIPTIONS[3];
        }
    }

    public void atEndOfTurnPreEndTurnCards(boolean isPlayer) {
        flash();
        addToBot((AbstractGameAction) new GainBlockAction(this.owner, this.owner, this.amount));
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * MetallicizePower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class ConstrictedPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Constricted");
    public static final String POWER_ID = "Constricted";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public AbstractCreature source;

    public ConstrictedPower(AbstractCreature target, AbstractCreature source, int fadeAmt) {
        this.name = NAME;
        this.ID = "Constricted";
        this.owner = target;
        this.source = source;
        this.amount = fadeAmt;
        updateDescription();
        loadRegion("constricted");
        this.type = PowerType.DEBUFF;

        this.priority = 105;
    }

    public void playApplyPowerSfx() {
        CardCrawlGame.sound.play("POWER_CONSTRICTED", 0.05F);
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
    }

    public void atEndOfTurn(boolean isPlayer) {
        flashWithoutSound();
        playApplyPowerSfx();
        addToBot((AbstractGameAction) new DamageAction(this.owner,
                new DamageInfo(this.source, this.amount, DamageInfo.DamageType.THORNS)));
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * ConstrictedPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


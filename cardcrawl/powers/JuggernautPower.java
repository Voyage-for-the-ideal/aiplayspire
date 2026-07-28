package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageRandomEnemyAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class JuggernautPower extends AbstractPower {
    public static final String POWER_ID = "Juggernaut";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Juggernaut");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public JuggernautPower(AbstractCreature owner, int newAmount) {
        this.name = NAME;
        this.ID = "Juggernaut";
        this.owner = owner;
        this.amount = newAmount;
        updateDescription();
        loadRegion("juggernaut");
    }

    public void onGainedBlock(float blockAmount) {
        if (blockAmount > 0.0F) {
            flash();
            addToBot((AbstractGameAction) new DamageRandomEnemyAction(
                    new DamageInfo(this.owner, this.amount, DamageInfo.DamageType.THORNS),
                    AbstractGameAction.AttackEffect.SLASH_HORIZONTAL));
        }
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * JuggernautPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


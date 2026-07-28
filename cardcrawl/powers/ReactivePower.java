package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RollMoveAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class ReactivePower extends AbstractPower {
    public static final String POWER_ID = "Compulsive";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Compulsive");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public ReactivePower(AbstractCreature owner) {
        this.name = NAME;
        this.ID = "Compulsive";
        this.owner = owner;
        updateDescription();
        loadRegion("reactive");
        this.priority = 50;
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0];
    }

    public int onAttacked(DamageInfo info, int damageAmount) {
        if (info.owner != null && info.type != DamageInfo.DamageType.HP_LOSS
                && info.type != DamageInfo.DamageType.THORNS && damageAmount > 0
                && damageAmount < this.owner.currentHealth) {

            flash();
            addToBot((AbstractGameAction) new RollMoveAction((AbstractMonster) this.owner));
        }
        return damageAmount;
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * ReactivePower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction;
import com.megacrit.cardcrawl.actions.common.LoseHPAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class CombustPower extends AbstractPower {
    public static final String POWER_ID = "Combust";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Combust");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    private int hpLoss;

    public CombustPower(AbstractCreature owner, int hpLoss, int damageAmount) {
        this.name = NAME;
        this.ID = "Combust";
        this.owner = owner;
        this.amount = damageAmount;
        this.hpLoss = hpLoss;
        updateDescription();
        loadRegion("combust");
    }

    public void atEndOfTurn(boolean isPlayer) {
        if (!AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
            flash();
            addToBot((AbstractGameAction) new LoseHPAction(this.owner, this.owner, this.hpLoss,
                    AbstractGameAction.AttackEffect.FIRE));
            addToBot((AbstractGameAction) new DamageAllEnemiesAction(null,

                    DamageInfo.createDamageMatrix(this.amount, true), DamageInfo.DamageType.THORNS,
                    AbstractGameAction.AttackEffect.FIRE));
        }
    }

    public void stackPower(int stackAmount) {
        this.fontScale = 8.0F;
        this.amount += stackAmount;
        this.hpLoss++;
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.hpLoss + DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[2];
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * CombustPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


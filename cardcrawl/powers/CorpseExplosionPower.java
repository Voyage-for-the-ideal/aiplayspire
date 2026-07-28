package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class CorpseExplosionPower extends AbstractPower {
    public static final String POWER_ID = "CorpseExplosionPower";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("CorpseExplosionPower");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public CorpseExplosionPower(AbstractCreature owner) {
        this.name = NAME;
        this.ID = "CorpseExplosionPower";
        this.owner = owner;
        this.amount = 1;
        this.type = PowerType.DEBUFF;
        updateDescription();
        loadRegion("cExplosion");
    }

    public void onDeath() {
        if (!AbstractDungeon.getMonsters().areMonstersBasicallyDead() &&
                this.owner.currentHealth <= 0) {
            addToBot((AbstractGameAction) new DamageAllEnemiesAction(null,

                    DamageInfo.createDamageMatrix(this.owner.maxHealth * this.amount, true),
                    DamageInfo.DamageType.THORNS, AbstractGameAction.AttackEffect.FIRE));
        }
    }

    public void updateDescription() {
        if (this.amount == 1) {
            this.description = DESCRIPTIONS[0];
        } else {
            this.description = DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[2];
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * CorpseExplosionPower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.ReducePowerAction;
import com.megacrit.cardcrawl.actions.common.SuicideAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.ExplosionSmallEffect;

public class ExplosivePower extends AbstractPower {
    public static final String POWER_ID = "Explosive";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Explosive");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;
    private static final int DAMAGE_AMOUNT = 30;

    public ExplosivePower(AbstractCreature owner, int damage) {
        this.name = NAME;
        this.ID = "Explosive";
        this.owner = owner;
        this.amount = damage;
        updateDescription();
        loadRegion("explosive");
    }

    public void updateDescription() {
        if (this.amount == 1) {
            this.description = DESCRIPTIONS[3] + '\036' + DESCRIPTIONS[2];
        } else {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1] + '\036' + DESCRIPTIONS[2];
        }
    }

    public void duringTurn() {
        if (this.amount == 1 && !this.owner.isDying) {
            addToBot((AbstractGameAction) new VFXAction(
                    (AbstractGameEffect) new ExplosionSmallEffect(this.owner.hb.cX, this.owner.hb.cY), 0.1F));
            addToBot((AbstractGameAction) new SuicideAction((AbstractMonster) this.owner));
            DamageInfo damageInfo = new DamageInfo(this.owner, 30, DamageInfo.DamageType.THORNS);
            addToBot((AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player, damageInfo,
                    AbstractGameAction.AttackEffect.FIRE, true));
        } else {
            addToBot((AbstractGameAction) new ReducePowerAction(this.owner, this.owner, "Explosive", 1));
            updateDescription();
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * ExplosivePower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


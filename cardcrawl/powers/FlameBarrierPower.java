package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FlameBarrierPower
        extends AbstractPower {
    private static final Logger logger = LogManager.getLogger(FlameBarrierPower.class.getName());
    public static final String POWER_ID = "Flame Barrier";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Flame Barrier");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public FlameBarrierPower(AbstractCreature owner, int thornsDamage) {
        this.name = NAME;
        this.ID = "Flame Barrier";
        this.owner = owner;
        this.amount = thornsDamage;
        updateDescription();
        loadRegion("flameBarrier");
    }

    public void stackPower(int stackAmount) {
        if (this.amount == -1) {
            logger.info(this.name + " does not stack");
            return;
        }
        this.fontScale = 8.0F;
        this.amount += stackAmount;
        updateDescription();
    }

    public int onAttacked(DamageInfo info, int damageAmount) {
        if (info.owner != null && info.type != DamageInfo.DamageType.THORNS
                && info.type != DamageInfo.DamageType.HP_LOSS && info.owner != this.owner) {

            flash();
            addToTop((AbstractGameAction) new DamageAction(info.owner,
                    new DamageInfo(this.owner, this.amount, DamageInfo.DamageType.THORNS),
                    AbstractGameAction.AttackEffect.FIRE));
        }
        return damageAmount;
    }

    public void atStartOfTurn() {
        addToBot((AbstractGameAction) new RemoveSpecificPowerAction((AbstractCreature) AbstractDungeon.player,
                (AbstractCreature) AbstractDungeon.player, "Flame Barrier"));
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * FlameBarrierPower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


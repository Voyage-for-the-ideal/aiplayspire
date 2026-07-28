package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class ApplyPowerToRandomEnemyAction
        extends AbstractGameAction {
    private AbstractPower powerToApply;
    private boolean isFast;
    private AttackEffect effect;

    public ApplyPowerToRandomEnemyAction(AbstractCreature source, AbstractPower powerToApply, int stackAmount,
            boolean isFast, AttackEffect effect) {
        setValues(null, source, stackAmount);
        this.powerToApply = powerToApply;
        this.isFast = isFast;
        this.effect = effect;
    }

    public ApplyPowerToRandomEnemyAction(AbstractCreature source, AbstractPower powerToApply, int stackAmount,
            boolean isFast) {
        this(source, powerToApply, stackAmount, isFast, AttackEffect.NONE);
    }

    public ApplyPowerToRandomEnemyAction(AbstractCreature source, AbstractPower powerToApply, int stackAmount) {
        this(source, powerToApply, stackAmount, false);
    }

    public ApplyPowerToRandomEnemyAction(AbstractCreature source, AbstractPower powerToApply) {
        this(source, powerToApply, -1);
    }

    public void update() {
        this.target = (AbstractCreature) AbstractDungeon.getMonsters().getRandomMonster(null, true,
                AbstractDungeon.cardRandomRng);
        this.powerToApply.owner = this.target;
        if (this.target != null) {
            addToTop(new ApplyPowerAction(this.target, this.source, this.powerToApply, this.amount, this.isFast,
                    this.effect));
        }
        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * ApplyPowerToRandomEnemyAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




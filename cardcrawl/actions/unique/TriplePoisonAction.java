package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.PoisonPower;

public class TriplePoisonAction extends AbstractGameAction {
    public TriplePoisonAction(AbstractCreature target, AbstractCreature source) {
        this.target = target;
        this.source = source;
        this.actionType = ActionType.DEBUFF;
        this.attackEffect = AttackEffect.FIRE;
    }

    public void update() {
        if (this.target.hasPower("Poison")) {
            addToTop((AbstractGameAction) new ApplyPowerAction(this.target, this.source,
                    (AbstractPower) new PoisonPower(this.target, this.source,

                            (this.target.getPower("Poison")).amount * 2),
                    (this.target.getPower("Poison")).amount * 2));
        }
        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * TriplePoisonAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




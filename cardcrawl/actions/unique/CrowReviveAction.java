package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.TintEffect;

public class CrowReviveAction
        extends AbstractGameAction {
    public CrowReviveAction(AbstractMonster target, AbstractCreature source) {
        setValues((AbstractCreature) target, source, 0);
        this.actionType = ActionType.SPECIAL;
    }

    public void update() {
        if (this.duration == 0.5F &&
                this.target instanceof AbstractMonster) {
            this.target.isDying = false;
            this.target.heal(this.target.maxHealth);
            this.target.healthBarRevivedEvent();
            ((AbstractMonster) this.target).deathTimer = 0.0F;
            ((AbstractMonster) this.target).tint = new TintEffect();
            ((AbstractMonster) this.target).tintFadeOutCalled = false;
            ((AbstractMonster) this.target).isDead = false;
            this.target.powers.clear();
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * CrowReviveAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




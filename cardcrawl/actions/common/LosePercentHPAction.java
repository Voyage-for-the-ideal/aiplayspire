package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class LosePercentHPAction extends AbstractGameAction {
    public LosePercentHPAction(int percent) {
        this.amount = percent;
    }

    public void update() {
        float percentConversion = this.amount / 100.0F;
        int amountToLose = (int) (AbstractDungeon.player.currentHealth * percentConversion);
        addToTop(new LoseHPAction((AbstractCreature) AbstractDungeon.player, (AbstractCreature) AbstractDungeon.player,
                amountToLose, AttackEffect.FIRE));
        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * LosePercentHPAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




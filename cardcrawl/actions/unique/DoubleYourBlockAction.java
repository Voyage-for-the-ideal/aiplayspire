package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;

public class DoubleYourBlockAction
        extends AbstractGameAction {
    public DoubleYourBlockAction(AbstractCreature target) {
        this.duration = 0.5F;
        this.actionType = ActionType.BLOCK;
        this.target = target;
    }

    public void update() {
        if (this.duration == 0.5F &&
                this.target != null && this.target.currentBlock > 0) {
            AbstractDungeon.effectList
                    .add(new FlashAtkImgEffect(this.target.hb.cX, this.target.hb.cY, AttackEffect.SHIELD));
            this.target.addBlock(this.target.currentBlock);
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * DoubleYourBlockAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




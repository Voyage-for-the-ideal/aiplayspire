package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.combat.MoveNameEffect;

public class ShowMoveNameAction
        extends AbstractGameAction {
    private String msg;

    public ShowMoveNameAction(AbstractMonster source, String msg) {
        setValues((AbstractCreature) source, (AbstractCreature) source);
        this.msg = msg;
        this.actionType = ActionType.TEXT;
    }

    public ShowMoveNameAction(AbstractMonster source) {
        setValues((AbstractCreature) source, (AbstractCreature) source);
        this.msg = source.moveName;
        source.moveName = null;
        this.actionType = ActionType.TEXT;
    }

    public void update() {
        if (this.source != null && !this.source.isDying) {
            AbstractDungeon.effectList.add(new MoveNameEffect(this.source.hb.cX - this.source.animX,
                    this.source.hb.cY + this.source.hb.height / 2.0F, this.msg));
        }

        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * ShowMoveNameAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




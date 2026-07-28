package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.ExhaustAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class FiendFireAction extends AbstractGameAction {
    private DamageInfo info;
    private float startingDuration;

    public FiendFireAction(AbstractCreature target, DamageInfo info) {
        this.info = info;
        setValues(target, info);
        this.actionType = ActionType.WAIT;
        this.attackEffect = AttackEffect.FIRE;
        this.startingDuration = Settings.ACTION_DUR_FAST;
        this.duration = this.startingDuration;
    }

    public void update() {
        int count = AbstractDungeon.player.hand.size();
        int i;
        for (i = 0; i < count; i++) {
            addToTop((AbstractGameAction) new DamageAction(this.target, this.info, AttackEffect.FIRE));
        }
        for (i = 0; i < count; i++) {
            if (Settings.FAST_MODE) {
                addToTop((AbstractGameAction) new ExhaustAction(1, true, true, false, Settings.ACTION_DUR_XFAST));
            } else {
                addToTop((AbstractGameAction) new ExhaustAction(1, true, true));
            }
        }

        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * FiendFireAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class MindBlastAction
        extends AbstractGameAction {
    public MindBlastAction(AbstractCreature target) {
        setValues(target, (AbstractCreature) AbstractDungeon.player);
        this.duration = Settings.ACTION_DUR_FAST;
        this.actionType = ActionType.DAMAGE;
    }

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST &&
                this.target != null) {
            DamageInfo info = new DamageInfo(this.source, AbstractDungeon.player.drawPile.size());
            info.applyPowers(this.source, this.target);
            addToTop((AbstractGameAction) new DamageAction(this.target, info, AttackEffect.NONE));
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * MindBlastAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




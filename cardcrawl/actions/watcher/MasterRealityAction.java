package com.megacrit.cardcrawl.actions.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.defect.LightningOrbEvokeAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class MasterRealityAction
        extends AbstractGameAction {
    public MasterRealityAction(int damageAmount) {
        this.amount = damageAmount;
        this.actionType = ActionType.DAMAGE;
        this.attackEffect = AttackEffect.NONE;
        this.duration = 0.01F;
    }

    private static final float DURATION = 0.01F;

    public void update() {
        if ((AbstractDungeon.getCurrRoom()).monsters.areMonstersBasicallyDead()) {
            AbstractDungeon.actionManager.clearPostCombatActions();
            this.isDone = true;

            return;
        }
        int count = 0;

        for (AbstractCard c : AbstractDungeon.player.hand.group) {
            if (c.selfRetain || c.retain) {
                count++;
            }
        }

        for (int i = 0; i < count; i++) {
            addToTop((AbstractGameAction) new LightningOrbEvokeAction(new DamageInfo(
                    (AbstractCreature) AbstractDungeon.player, this.amount, DamageInfo.DamageType.THORNS), false));
        }

        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\watcher\
 * MasterRealityAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




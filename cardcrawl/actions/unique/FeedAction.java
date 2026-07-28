package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;

public class FeedAction
        extends AbstractGameAction {
    private int increaseHpAmount;
    private DamageInfo info;
    private static final float DURATION = 0.1F;

    public FeedAction(AbstractCreature target, DamageInfo info, int maxHPAmount) {
        this.info = info;
        setValues(target, info);
        this.increaseHpAmount = maxHPAmount;
        this.actionType = ActionType.DAMAGE;
        this.duration = 0.1F;
    }

    public void update() {
        if (this.duration == 0.1F &&
                this.target != null) {
            AbstractDungeon.effectList
                    .add(new FlashAtkImgEffect(this.target.hb.cX, this.target.hb.cY, AttackEffect.NONE));
            this.target.damage(this.info);

            if ((((AbstractMonster) this.target).isDying || this.target.currentHealth <= 0) && !this.target.halfDead &&
                    !this.target.hasPower("Minion")) {
                AbstractDungeon.player.increaseMaxHp(this.increaseHpAmount, false);

                if (this.target instanceof com.megacrit.cardcrawl.monsters.beyond.Donu) {
                    UnlockTracker.unlockAchievement("DONUT");
                }
            }

            if ((AbstractDungeon.getCurrRoom()).monsters.areMonstersBasicallyDead()) {
                AbstractDungeon.actionManager.clearPostCombatActions();
            }
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * FeedAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




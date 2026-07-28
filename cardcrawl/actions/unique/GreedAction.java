package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.GainPennyEffect;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;

public class GreedAction
        extends AbstractGameAction {
    private int increaseGold;
    private DamageInfo info;
    private static final float DURATION = 0.1F;

    public GreedAction(AbstractCreature target, DamageInfo info, int goldAmount) {
        this.info = info;
        setValues(target, info);
        this.increaseGold = goldAmount;
        this.actionType = ActionType.DAMAGE;
        this.duration = 0.1F;
    }

    public void update() {
        if (this.duration == 0.1F &&
                this.target != null) {
            AbstractDungeon.effectList
                    .add(new FlashAtkImgEffect(this.target.hb.cX, this.target.hb.cY, AttackEffect.BLUNT_HEAVY));

            this.target.damage(this.info);

            if ((((AbstractMonster) this.target).isDying || this.target.currentHealth <= 0) && !this.target.halfDead &&
                    !this.target.hasPower("Minion")) {
                AbstractDungeon.player.gainGold(this.increaseGold);
                for (int i = 0; i < this.increaseGold; i++) {
                    AbstractDungeon.effectList.add(new GainPennyEffect(this.source, this.target.hb.cX,
                            this.target.hb.cY, this.source.hb.cX, this.source.hb.cY, true));
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
 * GreedAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




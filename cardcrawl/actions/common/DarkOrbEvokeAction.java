package com.megacrit.cardcrawl.actions.common;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;

public class DarkOrbEvokeAction
        extends AbstractGameAction {
    private DamageInfo info;
    private static final float DURATION = 0.1F;
    private static final float POST_ATTACK_WAIT_DUR = 0.1F;
    private boolean muteSfx = false;

    public DarkOrbEvokeAction(DamageInfo info, AttackEffect effect) {
        AbstractMonster weakestMonster = null;
        for (AbstractMonster m : (AbstractDungeon.getMonsters()).monsters) {
            if (!m.isDeadOrEscaped()) {
                if (weakestMonster == null) {
                    weakestMonster = m;
                    continue;
                }
                if (m.currentHealth < weakestMonster.currentHealth) {
                    weakestMonster = m;
                }
            }
        }

        this.info = info;
        setValues((AbstractCreature) weakestMonster, info);
        this.actionType = ActionType.DAMAGE;
        this.attackEffect = effect;
        this.duration = 0.1F;
    }

    public void update() {
        if ((shouldCancelAction() && this.info.type != DamageInfo.DamageType.THORNS) || this.target == null) {
            this.isDone = true;

            return;
        }
        if (this.duration == 0.1F) {
            this.info.output = AbstractOrb.applyLockOn(this.target, this.info.base);

            if (this.info.type != DamageInfo.DamageType.THORNS
                    && (this.info.owner.isDying || this.info.owner.halfDead)) {
                this.isDone = true;

                return;
            }
            AbstractDungeon.effectList
                    .add(new FlashAtkImgEffect(this.target.hb.cX, this.target.hb.cY, this.attackEffect, this.muteSfx));
        }

        tickDuration();

        if (this.isDone) {
            if (this.attackEffect == AttackEffect.POISON) {
                this.target.tint.color = Color.CHARTREUSE.cpy();
                this.target.tint.changeColor(Color.WHITE.cpy());
            } else if (this.attackEffect == AttackEffect.FIRE) {
                this.target.tint.color = Color.RED.cpy();
                this.target.tint.changeColor(Color.WHITE.cpy());
            }
            this.target.damage(this.info);

            if ((AbstractDungeon.getCurrRoom()).monsters.areMonstersBasicallyDead()) {
                AbstractDungeon.actionManager.clearPostCombatActions();
            }

            if (!Settings.FAST_MODE)
                addToTop((AbstractGameAction) new WaitAction(0.1F));
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * DarkOrbEvokeAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




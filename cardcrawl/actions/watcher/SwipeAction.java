package com.megacrit.cardcrawl.actions.watcher;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;

public class SwipeAction
        extends AbstractGameAction {
    private DamageInfo info;
    private static final float POST_ATTACK_WAIT_DUR = 0.1F;
    private boolean skipWait = false;

    public SwipeAction(AbstractCreature target, DamageInfo info) {
        this.info = info;
        setValues(target, info);
        this.actionType = ActionType.DAMAGE;
        this.attackEffect = AttackEffect.SLASH_VERTICAL;
        this.duration = Settings.ACTION_DUR_XFAST;
    }

    public void update() {
        if (shouldCancelAction() && this.info.type != DamageInfo.DamageType.THORNS) {
            this.isDone = true;

            return;
        }
        if (this.duration == Settings.ACTION_DUR_XFAST) {
            if (this.info.type != DamageInfo.DamageType.THORNS
                    && (this.info.owner.isDying || this.info.owner.halfDead)) {
                this.isDone = true;

                return;
            }
            AbstractDungeon.effectList
                    .add(new FlashAtkImgEffect(this.target.hb.cX, this.target.hb.cY, this.attackEffect, false));
        }

        tickDuration();

        if (this.isDone) {
            if (this.attackEffect == AttackEffect.POISON) {
                this.target.tint.color.set(Color.CHARTREUSE.cpy());
                this.target.tint.changeColor(Color.WHITE.cpy());
            } else if (this.attackEffect == AttackEffect.FIRE) {
                this.target.tint.color.set(Color.RED);
                this.target.tint.changeColor(Color.WHITE.cpy());
            }
            this.target.damage(this.info);

            if ((AbstractDungeon.getCurrRoom()).monsters.areMonstersBasicallyDead()) {
                AbstractDungeon.actionManager.clearPostCombatActions();
            }

            if (!this.skipWait && !Settings.FAST_MODE)
                addToTop((AbstractGameAction) new WaitAction(0.1F));
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\watcher\
 * SwipeAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




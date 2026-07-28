package com.megacrit.cardcrawl.actions.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.WallopEffect;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;

public class WallopAction
        extends AbstractGameAction {
    public WallopAction(AbstractCreature target, DamageInfo info) {
        this.info = info;
        setValues(target, info);
        this.actionType = ActionType.DAMAGE;
        this.startDuration = Settings.ACTION_DUR_FAST;
        this.duration = this.startDuration;
    }
    private DamageInfo info;

    public void update() {
        if (shouldCancelAction()) {
            this.isDone = true;

            return;
        }
        tickDuration();

        if (this.isDone) {
            AbstractDungeon.effectList
                    .add(new FlashAtkImgEffect(this.target.hb.cX, this.target.hb.cY, AttackEffect.BLUNT_HEAVY, false));

            this.target.damage(this.info);
            if (this.target.lastDamageTaken > 0) {
                addToTop((AbstractGameAction) new GainBlockAction(this.source, this.target.lastDamageTaken));
                if (this.target.hb != null) {
                    addToTop((AbstractGameAction) new VFXAction((AbstractGameEffect) new WallopEffect(
                            this.target.lastDamageTaken, this.target.hb.cX, this.target.hb.cY)));
                }
            }

            if ((AbstractDungeon.getCurrRoom()).monsters.areMonstersBasicallyDead()) {
                AbstractDungeon.actionManager.clearPostCombatActions();
            } else {
                addToTop((AbstractGameAction) new WaitAction(0.1F));
            }
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\watcher\
 * WallopAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




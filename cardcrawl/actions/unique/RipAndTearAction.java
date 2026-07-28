package com.megacrit.cardcrawl.actions.unique;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.combat.RipAndTearEffect;

public class RipAndTearAction extends AbstractGameAction {
    private DamageInfo info;
    private int numTimes;

    public RipAndTearAction(AbstractCreature target, DamageInfo info, int numTimes) {
        this.info = info;
        this.target = target;
        this.actionType = ActionType.DAMAGE;
        if (Settings.FAST_MODE) {
            this.startDuration = 0.05F;
        } else {
            this.startDuration = 0.2F;
        }
        this.duration = this.startDuration;
        this.numTimes = numTimes;
    }

    public void update() {
        if (this.target == null) {
            this.isDone = true;

            return;
        }
        if ((AbstractDungeon.getCurrRoom()).monsters.areMonstersBasicallyDead()) {
            AbstractDungeon.actionManager.clearPostCombatActions();
            this.isDone = true;

            return;
        }
        if (this.duration == this.startDuration) {
            AbstractDungeon.effectsQueue
                    .add(new RipAndTearEffect(this.target.hb.cX, this.target.hb.cY, Color.RED, Color.GOLD));
        }

        this.duration -= Gdx.graphics.getDeltaTime();

        if (this.duration < 0.0F) {
            if (this.target.currentHealth > 0) {
                this.info.applyPowers(this.info.owner, this.target);
                this.target.damage(this.info);

                if (this.numTimes > 1 && !AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
                    this.numTimes--;
                    addToTop(new RipAndTearAction(

                            (AbstractCreature) AbstractDungeon.getMonsters().getRandomMonster(null, true,
                                    AbstractDungeon.cardRandomRng),
                            this.info, this.numTimes));
                }

                if (Settings.FAST_MODE) {
                    addToTop((AbstractGameAction) new WaitAction(0.1F));
                } else {
                    addToTop((AbstractGameAction) new WaitAction(0.2F));
                }
            }
            this.isDone = true;
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * RipAndTearAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




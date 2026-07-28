package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.PoisonPower;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.PotionBounceEffect;

public class BouncingFlaskAction extends AbstractGameAction {
    private static final float DURATION = 0.01F;
    private static final float POST_ATTACK_WAIT_DUR = 0.1F;

    public BouncingFlaskAction(AbstractCreature target, int amount, int numTimes) {
        this.target = target;
        this.actionType = ActionType.DEBUFF;
        this.duration = 0.01F;
        this.numTimes = numTimes;
        this.amount = amount;
    }
    private int numTimes;
    private int amount;

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

        if (this.numTimes > 1 && !AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
            this.numTimes--;
            AbstractMonster randomMonster = AbstractDungeon.getMonsters().getRandomMonster(null, true,
                    AbstractDungeon.cardRandomRng);

            addToTop(new BouncingFlaskAction((AbstractCreature) randomMonster, this.amount, this.numTimes));
            addToTop((AbstractGameAction) new VFXAction((AbstractGameEffect) new PotionBounceEffect(this.target.hb.cX,
                    this.target.hb.cY, randomMonster.hb.cX, randomMonster.hb.cY), 0.4F));
        }

        if (this.target.currentHealth > 0) {
            addToTop((AbstractGameAction) new ApplyPowerAction(
                    this.target, (AbstractCreature) AbstractDungeon.player, (AbstractPower) new PoisonPower(this.target,
                            (AbstractCreature) AbstractDungeon.player, this.amount),
                    this.amount, true, AttackEffect.POISON));

            addToTop((AbstractGameAction) new WaitAction(0.1F));
        }

        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * BouncingFlaskAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




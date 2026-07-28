package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.beyond.SnakeDagger;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.StrengthPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.vfx.TintEffect;

public class ReviveMonsterAction
        extends AbstractGameAction {
    private boolean healingEffect;

    public ReviveMonsterAction(AbstractMonster target, AbstractCreature source, boolean healEffect) {
        setValues((AbstractCreature) target, source, 0);
        this.actionType = ActionType.SPECIAL;
        if (AbstractDungeon.player.hasRelic("Philosopher's Stone")) {
            target.addPower((AbstractPower) new StrengthPower((AbstractCreature) target, 1));
        }

        this.healingEffect = healEffect;
    }

    public void update() {
        if (this.duration == 0.5F &&
                this.target instanceof AbstractMonster) {
            this.target.isDying = false;
            this.target.heal(this.target.maxHealth, this.healingEffect);
            this.target.healthBarRevivedEvent();
            ((AbstractMonster) this.target).deathTimer = 0.0F;
            ((AbstractMonster) this.target).tint = new TintEffect();
            ((AbstractMonster) this.target).tintFadeOutCalled = false;
            ((AbstractMonster) this.target).isDead = false;
            this.target.powers.clear();

            if (this.target instanceof SnakeDagger) {
                ((SnakeDagger) this.target).firstMove = true;
                ((SnakeDagger) this.target).initializeAnimation();
            }

            if (this.target instanceof AbstractMonster) {
                for (AbstractRelic r : AbstractDungeon.player.relics) {
                    r.onSpawnMonster((AbstractMonster) this.target);
                }
            }

            ((AbstractMonster) this.target).intent = AbstractMonster.Intent.NONE;
            ((AbstractMonster) this.target).rollMove();
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * ReviveMonsterAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




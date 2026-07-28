package com.megacrit.cardcrawl.actions.defect;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;

public class DamageAllButOneEnemyAction
        extends AbstractGameAction {
    public int[] damage;
    private boolean firstFrame = true;

    public DamageAllButOneEnemyAction(AbstractCreature source, AbstractCreature target, int[] amount,
            DamageInfo.DamageType type, AttackEffect effect, boolean isFast) {
        setValues(target, source, amount[0]);
        this.damage = amount;
        this.actionType = ActionType.DAMAGE;
        this.damageType = type;
        this.attackEffect = effect;
        if (isFast) {
            this.duration = Settings.ACTION_DUR_XFAST;
        } else {
            this.duration = Settings.ACTION_DUR_FAST;
        }
    }

    public DamageAllButOneEnemyAction(AbstractCreature source, AbstractCreature target, int[] amount,
            DamageInfo.DamageType type, AttackEffect effect) {
        this(source, target, amount, type, effect, false);
    }

    public void update() {
        if (this.firstFrame) {
            boolean playedMusic = false;
            int temp = (AbstractDungeon.getCurrRoom()).monsters.monsters.size();

            for (int i = 0; i < temp; i++) {
                if ((AbstractDungeon.getCurrRoom()).monsters.monsters.get(i) != this.target &&
                        !((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).isDying
                        && ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters
                                .get(i)).currentHealth > 0
                        && !((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).isEscaping) {
                    if (playedMusic) {
                        AbstractDungeon.effectList.add(new FlashAtkImgEffect(

                                ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).hb.cX,
                                ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).hb.cY,
                                this.attackEffect, true));
                    } else {

                        playedMusic = true;
                        AbstractDungeon.effectList.add(new FlashAtkImgEffect(

                                ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).hb.cX,
                                ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).hb.cY,
                                this.attackEffect));
                    }
                }
            }

            this.firstFrame = false;
        }

        tickDuration();

        if (this.isDone) {
            for (AbstractPower p : AbstractDungeon.player.powers) {
                p.onDamageAllEnemies(this.damage);
            }

            int temp = (AbstractDungeon.getCurrRoom()).monsters.monsters.size();
            for (int i = 0; i < temp; i++) {
                if ((AbstractDungeon.getCurrRoom()).monsters.monsters.get(i) != this.target &&
                        !((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i))
                                .isDeadOrEscaped()) {
                    if (this.attackEffect == AttackEffect.POISON) {
                        ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters
                                .get(i)).tint.color = Color.CHARTREUSE.cpy();
                        ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).tint
                                .changeColor(Color.WHITE.cpy());
                    } else if (this.attackEffect == AttackEffect.FIRE) {
                        ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters
                                .get(i)).tint.color = Color.RED.cpy();
                        ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).tint
                                .changeColor(Color.WHITE.cpy());
                    }
                    DamageInfo info = new DamageInfo(this.source, this.damage[i], this.damageType);
                    info.applyPowers(this.source, (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i));
                    ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).damage(info);
                }

                if ((AbstractDungeon.getCurrRoom()).monsters.areMonstersBasicallyDead()) {
                    AbstractDungeon.actionManager.clearPostCombatActions();
                }
                addToTop((AbstractGameAction) new WaitAction(0.1F));
            }
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * DamageAllButOneEnemyAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




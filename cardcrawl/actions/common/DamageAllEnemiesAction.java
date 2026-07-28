package com.megacrit.cardcrawl.actions.common;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.vfx.combat.FlashAtkImgEffect;

public class DamageAllEnemiesAction
        extends AbstractGameAction {
    public int[] damage;
    private int baseDamage;
    private boolean firstFrame = true, utilizeBaseDamage = false;

    public DamageAllEnemiesAction(AbstractCreature source, int[] amount, DamageInfo.DamageType type,
            AttackEffect effect, boolean isFast) {
        this.source = source;
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

    public DamageAllEnemiesAction(AbstractCreature source, int[] amount, DamageInfo.DamageType type,
            AttackEffect effect) {
        this(source, amount, type, effect, false);
    }

    public DamageAllEnemiesAction(AbstractPlayer player, int baseDamage, DamageInfo.DamageType type,
            AttackEffect effect) {
        this((AbstractCreature) player, (int[]) null, type, effect, false);
        this.baseDamage = baseDamage;
        this.utilizeBaseDamage = true;
    }

    public void update() {
        if (this.firstFrame) {
            boolean playedMusic = false;
            int temp = (AbstractDungeon.getCurrRoom()).monsters.monsters.size();
            if (this.utilizeBaseDamage) {
                this.damage = DamageInfo.createDamageMatrix(this.baseDamage);
            }

            for (int i = 0; i < temp; i++) {
                if (!((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).isDying &&
                        ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).currentHealth > 0
                        &&
                        !((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).isEscaping) {
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
                if (!((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).isDeadOrEscaped()) {
                    if (this.attackEffect == AttackEffect.POISON) {
                        ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).tint.color
                                .set(Color.CHARTREUSE);
                        ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).tint
                                .changeColor(Color.WHITE.cpy());
                    } else if (this.attackEffect == AttackEffect.FIRE) {
                        ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).tint.color
                                .set(Color.RED);
                        ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i)).tint
                                .changeColor(Color.WHITE.cpy());
                    }
                    ((AbstractMonster) (AbstractDungeon.getCurrRoom()).monsters.monsters.get(i))
                            .damage(new DamageInfo(this.source, this.damage[i], this.damageType));
                }
            }

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
 * DamageAllEnemiesAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




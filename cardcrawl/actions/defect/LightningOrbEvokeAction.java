package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.LightningEffect;

public class LightningOrbEvokeAction extends AbstractGameAction {
    private DamageInfo info;
    private boolean hitAll;

    public LightningOrbEvokeAction(DamageInfo info, boolean hitAll) {
        this.info = info;
        this.actionType = ActionType.DAMAGE;
        this.attackEffect = AttackEffect.NONE;
        this.hitAll = hitAll;
    }

    public void update() {
        if (!this.hitAll) {
            AbstractMonster abstractMonster = AbstractDungeon.getRandomMonster();

            if (abstractMonster != null) {
                float speedTime = 0.1F;
                if (!AbstractDungeon.player.orbs.isEmpty()) {
                    speedTime = 0.2F / AbstractDungeon.player.orbs.size();
                }
                if (Settings.FAST_MODE) {
                    speedTime = 0.0F;
                }

                this.info.output = AbstractOrb.applyLockOn((AbstractCreature) abstractMonster, this.info.base);
                addToTop((AbstractGameAction) new DamageAction((AbstractCreature) abstractMonster, this.info,
                        AttackEffect.NONE, true));
                addToTop((AbstractGameAction) new VFXAction(
                        (AbstractGameEffect) new LightningEffect(((AbstractCreature) abstractMonster).drawX,
                                ((AbstractCreature) abstractMonster).drawY),
                        speedTime));
                addToTop((AbstractGameAction) new SFXAction("ORB_LIGHTNING_EVOKE"));
            }
        } else {
            float speedTime = 0.2F / AbstractDungeon.player.orbs.size();
            if (Settings.FAST_MODE) {
                speedTime = 0.0F;
            }
            addToTop((AbstractGameAction) new DamageAllEnemiesAction((AbstractCreature) AbstractDungeon.player,

                    DamageInfo.createDamageMatrix(this.info.base, true, true), DamageInfo.DamageType.THORNS,
                    AttackEffect.NONE));

            for (AbstractMonster m3 : (AbstractDungeon.getMonsters()).monsters) {
                if (!m3.isDeadOrEscaped() && !m3.halfDead) {
                    addToTop((AbstractGameAction) new VFXAction(
                            (AbstractGameEffect) new LightningEffect(m3.drawX, m3.drawY), speedTime));
                }
            }
            addToTop((AbstractGameAction) new SFXAction("ORB_LIGHTNING_EVOKE"));
        }

        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * LightningOrbEvokeAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




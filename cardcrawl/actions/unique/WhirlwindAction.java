package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.CleaveEffect;
import com.megacrit.cardcrawl.vfx.combat.WhirlwindEffect;

public class WhirlwindAction
        extends AbstractGameAction {
    public int[] multiDamage;
    private boolean freeToPlayOnce = false;
    private int energyOnUse = -1;

    private DamageInfo.DamageType damageType;

    private AbstractPlayer p;

    public WhirlwindAction(AbstractPlayer p, int[] multiDamage, DamageInfo.DamageType damageType,
            boolean freeToPlayOnce, int energyOnUse) {
        this.multiDamage = multiDamage;
        this.damageType = damageType;
        this.p = p;
        this.freeToPlayOnce = freeToPlayOnce;
        this.duration = Settings.ACTION_DUR_XFAST;
        this.actionType = ActionType.SPECIAL;
        this.energyOnUse = energyOnUse;
    }

    public void update() {
        int effect = EnergyPanel.totalCount;
        if (this.energyOnUse != -1) {
            effect = this.energyOnUse;
        }

        if (this.p.hasRelic("Chemical X")) {
            effect += 2;
            this.p.getRelic("Chemical X").flash();
        }

        if (effect > 0) {
            for (int i = 0; i < effect; i++) {
                if (i == 0) {
                    addToBot((AbstractGameAction) new SFXAction("ATTACK_WHIRLWIND"));
                    addToBot((AbstractGameAction) new VFXAction((AbstractGameEffect) new WhirlwindEffect(), 0.0F));
                }

                addToBot((AbstractGameAction) new SFXAction("ATTACK_HEAVY"));
                addToBot((AbstractGameAction) new VFXAction((AbstractCreature) this.p,
                        (AbstractGameEffect) new CleaveEffect(), 0.0F));
                addToBot((AbstractGameAction) new DamageAllEnemiesAction((AbstractCreature) this.p, this.multiDamage,
                        this.damageType, AttackEffect.NONE, true));
            }

            if (!this.freeToPlayOnce) {
                this.p.energy.use(EnergyPanel.totalCount);
            }
        }
        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * WhirlwindAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.OmegaFlashEffect;

public class OmegaPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("OmegaPower");
    public static final String POWER_ID = "OmegaPower";

    public OmegaPower(AbstractCreature owner, int newAmount) {
        this.name = powerStrings.NAME;
        this.ID = "OmegaPower";
        this.owner = owner;
        this.amount = newAmount;
        updateDescription();
        loadRegion("omega");
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
    }

    public void atEndOfTurn(boolean isPlayer) {
        if (isPlayer) {
            flash();

            for (AbstractMonster m : (AbstractDungeon.getMonsters()).monsters) {
                if (m != null && !m.isDeadOrEscaped()) {
                    if (Settings.FAST_MODE) {
                        addToBot((AbstractGameAction) new VFXAction(
                                (AbstractGameEffect) new OmegaFlashEffect(m.hb.cX, m.hb.cY)));
                        continue;
                    }
                    addToBot((AbstractGameAction) new VFXAction(
                            (AbstractGameEffect) new OmegaFlashEffect(m.hb.cX, m.hb.cY), 0.2F));
                }
            }

            addToBot((AbstractGameAction) new DamageAllEnemiesAction(null,

                    DamageInfo.createDamageMatrix(this.amount, true), DamageInfo.DamageType.THORNS,
                    AbstractGameAction.AttackEffect.FIRE, true));
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * OmegaPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


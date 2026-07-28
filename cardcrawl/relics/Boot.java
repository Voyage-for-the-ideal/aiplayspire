package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class Boot extends AbstractRelic {
    public static final String ID = "Boot";
    private static final int THRESHOLD = 5;

    public Boot() {
        super("Boot", "boot.png", RelicTier.COMMON, LandingSound.HEAVY);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\004' + this.DESCRIPTIONS[1];
    }

    public int onAttackToChangeDamage(DamageInfo info, int damageAmount) {
        if (info.owner != null && info.type != DamageInfo.DamageType.HP_LOSS
                && info.type != DamageInfo.DamageType.THORNS && damageAmount > 0 && damageAmount < 5) {

            flash();
            addToBot(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
            return 5;
        }
        return damageAmount;
    }

    public AbstractRelic makeCopy() {
        return new Boot();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Boot.class
 * Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


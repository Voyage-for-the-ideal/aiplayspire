package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class CharonsAshes
        extends AbstractRelic {
    public static final String ID = "Charon's Ashes";
    public static final int DMG = 3;

    public CharonsAshes() {
        super("Charon's Ashes", "ashes.png", RelicTier.RARE, LandingSound.MAGICAL);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\003' + this.DESCRIPTIONS[1];
    }

    public void onExhaust(AbstractCard card) {
        flash();

        addToTop((AbstractGameAction) new DamageAllEnemiesAction(null,

                DamageInfo.createDamageMatrix(3, true), DamageInfo.DamageType.THORNS,
                AbstractGameAction.AttackEffect.FIRE));

        for (AbstractMonster mo : (AbstractDungeon.getCurrRoom()).monsters.monsters) {
            if (!mo.isDead) {
                addToTop((AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) mo, this));
            }
        }
    }

    public AbstractRelic makeCopy() {
        return new CharonsAshes();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * CharonsAshes.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


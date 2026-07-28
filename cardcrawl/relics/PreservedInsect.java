package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class PreservedInsect extends AbstractRelic {
    private float MODIFIER_AMT = 0.25F;
    public static final String ID = "PreservedInsect";

    public PreservedInsect() {
        super("PreservedInsect", "insect.png", RelicTier.COMMON, LandingSound.FLAT);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\031' + this.DESCRIPTIONS[1];
    }

    public void atBattleStart() {
        if ((AbstractDungeon.getCurrRoom()).eliteTrigger) {
            flash();
            for (AbstractMonster m : (AbstractDungeon.getCurrRoom()).monsters.monsters) {
                if (m.currentHealth > (int) (m.maxHealth * (1.0F - this.MODIFIER_AMT))) {
                    m.currentHealth = (int) (m.maxHealth * (1.0F - this.MODIFIER_AMT));
                    m.healthBarUpdatedEvent();
                }
            }
            addToTop(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
        }
    }

    public boolean canSpawn() {
        return (Settings.isEndless || AbstractDungeon.floorNum <= 52);
    }

    public AbstractRelic makeCopy() {
        return new PreservedInsect();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * PreservedInsect.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


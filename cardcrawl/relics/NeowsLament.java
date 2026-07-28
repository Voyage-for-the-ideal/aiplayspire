package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class NeowsLament extends AbstractRelic {
    public NeowsLament() {
        super("NeowsBlessing", "lament.png", RelicTier.SPECIAL, LandingSound.FLAT);
        this.counter = 3;
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }
    public static final String ID = "NeowsBlessing";

    public void atBattleStart() {
        if (this.counter > 0) {
            this.counter--;
            if (this.counter == 0) {
                setCounter(-2);
                this.description = this.DESCRIPTIONS[1];
                this.tips.clear();
                this.tips.add(new PowerTip(this.name, this.description));
                initializeTips();
            }
            flash();
            for (AbstractMonster m : (AbstractDungeon.getCurrRoom()).monsters.monsters) {
                m.currentHealth = 1;
                m.healthBarUpdatedEvent();
            }
            addToTop(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
        }
    }

    public void setCounter(int setCounter) {
        this.counter = setCounter;
        if (setCounter <= 0) {
            usedUp();
        }
    }

    public AbstractRelic makeCopy() {
        return new NeowsLament();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\NeowsLament
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


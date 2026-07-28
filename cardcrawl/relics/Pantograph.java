package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.HealAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Pantograph extends AbstractRelic {
    public static final String ID = "Pantograph";
    private static final int HEAL_AMT = 25;

    public Pantograph() {
        super("Pantograph", "pantograph.png", RelicTier.UNCOMMON, LandingSound.CLINK);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\031' + this.DESCRIPTIONS[1];
    }

    public void atBattleStart() {
        for (AbstractMonster m : (AbstractDungeon.getMonsters()).monsters) {
            if (m.type == AbstractMonster.EnemyType.BOSS) {
                flash();
                addToTop((AbstractGameAction) new HealAction((AbstractCreature) AbstractDungeon.player,
                        (AbstractCreature) AbstractDungeon.player, 25, 0.0F));
                addToTop((AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player,
                        this));
                return;
            }
        }
    }

    public AbstractRelic makeCopy() {
        return new Pantograph();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Pantograph.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


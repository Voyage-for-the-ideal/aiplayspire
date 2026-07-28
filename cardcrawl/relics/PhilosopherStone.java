package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.StrengthPower;

public class PhilosopherStone extends AbstractRelic {
    public static final String ID = "Philosopher's Stone";

    public PhilosopherStone() {
        super("Philosopher's Stone", "philosopherStone.png", RelicTier.BOSS, LandingSound.CLINK);
    }
    public static final int STR = 1;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\001' + this.DESCRIPTIONS[1];
    }

    public void updateDescription(AbstractPlayer.PlayerClass c) {
        this.description = getUpdatedDescription();
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        initializeTips();
    }

    public void atBattleStart() {
        for (AbstractMonster m : (AbstractDungeon.getMonsters()).monsters) {
            addToTop((AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) m, this));
            m.addPower((AbstractPower) new StrengthPower((AbstractCreature) m, 1));
        }
        AbstractDungeon.onModifyPower();
    }

    public void onSpawnMonster(AbstractMonster monster) {
        monster.addPower((AbstractPower) new StrengthPower((AbstractCreature) monster, 1));
        AbstractDungeon.onModifyPower();
    }

    public void onEquip() {
        AbstractDungeon.player.energy.energyMaster++;
    }

    public void onUnequip() {
        AbstractDungeon.player.energy.energyMaster--;
    }

    public AbstractRelic makeCopy() {
        return new PhilosopherStone();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * PhilosopherStone.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


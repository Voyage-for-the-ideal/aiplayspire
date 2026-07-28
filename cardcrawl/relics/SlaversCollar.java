package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class SlaversCollar extends AbstractRelic {
    public static final String ID = "SlaversCollar";

    public SlaversCollar() {
        super("SlaversCollar", "collar.png", RelicTier.BOSS, LandingSound.FLAT);
    }

    public String getUpdatedDescription() {
        if (AbstractDungeon.player != null) {
            return setDescription(AbstractDungeon.player.chosenClass);
        }
        return setDescription((AbstractPlayer.PlayerClass) null);
    }

    private String setDescription(AbstractPlayer.PlayerClass c) {
        return this.DESCRIPTIONS[0];
    }

    public void updateDescription(AbstractPlayer.PlayerClass c) {
        this.description = setDescription(c);
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        initializeTips();
    }

    public void beforeEnergyPrep() {
        boolean isEliteOrBoss = (AbstractDungeon.getCurrRoom()).eliteTrigger;
        for (AbstractMonster m : (AbstractDungeon.getMonsters()).monsters) {
            if (m.type == AbstractMonster.EnemyType.BOSS) {
                isEliteOrBoss = true;
            }
        }

        if (isEliteOrBoss) {
            beginLongPulse();
            flash();
            AbstractDungeon.player.energy.energyMaster++;
        }
    }

    public void onVictory() {
        if (this.pulse) {
            AbstractDungeon.player.energy.energyMaster--;
            stopPulse();
        }
    }

    public AbstractRelic makeCopy() {
        return new SlaversCollar();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * SlaversCollar.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


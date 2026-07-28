package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class MeatOnTheBone extends AbstractRelic {
    public static final String ID = "Meat on the Bone";

    public MeatOnTheBone() {
        super("Meat on the Bone", "meat.png", RelicTier.UNCOMMON, LandingSound.HEAVY);
    }
    private static final int HEAL_AMT = 12;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\f' + this.DESCRIPTIONS[1];
    }

    public void onTrigger() {
        AbstractPlayer p = AbstractDungeon.player;
        if (p.currentHealth <= p.maxHealth / 2.0F && p.currentHealth > 0) {
            flash();
            addToTop(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
            p.heal(12);
            stopPulse();
        }
    }

    public void onBloodied() {
        flash();
        this.pulse = true;
    }

    public void onNotBloodied() {
        stopPulse();
    }

    public boolean canSpawn() {
        return (Settings.isEndless || AbstractDungeon.floorNum <= 48);
    }

    public AbstractRelic makeCopy() {
        return new MeatOnTheBone();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * MeatOnTheBone.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


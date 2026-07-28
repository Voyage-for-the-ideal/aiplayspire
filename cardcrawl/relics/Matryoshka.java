package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class Matryoshka extends AbstractRelic {
    public Matryoshka() {
        super("Matryoshka", "matryoshka.png", RelicTier.UNCOMMON, LandingSound.SOLID);
        this.counter = 2;
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }
    public static final String ID = "Matryoshka";

    public void onChestOpen(boolean bossChest) {
        if (!bossChest &&
                this.counter > 0) {
            this.counter--;

            flash();
            addToTop(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));

            if (AbstractDungeon.relicRng.randomBoolean(0.75F)) {
                AbstractDungeon.getCurrRoom().addRelicToRewards(RelicTier.COMMON);
            } else {
                AbstractDungeon.getCurrRoom().addRelicToRewards(RelicTier.UNCOMMON);
            }
            if (this.counter == 0) {
                setCounter(-2);
                this.description = this.DESCRIPTIONS[2];
            } else {
                this.description = this.DESCRIPTIONS[1];
            }
        }
    }

    public void setCounter(int setCounter) {
        this.counter = setCounter;
        if (setCounter == -2) {
            usedUp();
            this.counter = -2;
        }
    }

    public boolean canSpawn() {
        return (Settings.isEndless || AbstractDungeon.floorNum <= 40);
    }

    public AbstractRelic makeCopy() {
        return new Matryoshka();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Matryoshka.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


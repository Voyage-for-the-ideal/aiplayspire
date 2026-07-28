package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class RunicCube extends AbstractRelic {
    public static final String ID = "Runic Cube";

    public RunicCube() {
        super("Runic Cube", "runicCube.png", RelicTier.BOSS, LandingSound.FLAT);
    }
    private static final int NUM_CARDS = 1;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\001' + this.DESCRIPTIONS[1];
    }

    public void wasHPLost(int damageAmount) {
        if ((AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT &&
                damageAmount > 0) {
            flash();
            addToTop((AbstractGameAction) new DrawCardAction((AbstractCreature) AbstractDungeon.player, 1));
            addToTop(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
        }
    }

    public AbstractRelic makeCopy() {
        return new RunicCube();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\RunicCube.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


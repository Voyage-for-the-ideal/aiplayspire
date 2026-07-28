package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class CentennialPuzzle extends AbstractRelic {
    public static final String ID = "Centennial Puzzle";
    private static final int NUM_CARDS = 3;
    private static boolean usedThisCombat = false;

    public CentennialPuzzle() {
        super("Centennial Puzzle", "centennialPuzzle.png", RelicTier.COMMON, LandingSound.HEAVY);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\003' + this.DESCRIPTIONS[1];
    }

    public void atPreBattle() {
        usedThisCombat = false;
        this.pulse = true;
        beginPulse();
    }

    public void wasHPLost(int damageAmount) {
        if (damageAmount > 0 &&
                (AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT &&
                !usedThisCombat) {
            flash();
            this.pulse = false;
            addToTop((AbstractGameAction) new DrawCardAction((AbstractCreature) AbstractDungeon.player, 3));
            addToTop(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
            usedThisCombat = true;
            this.grayscale = true;
        }
    }

    public void justEnteredRoom(AbstractRoom room) {
        this.grayscale = false;
    }

    public void onVictory() {
        this.pulse = false;
    }

    public AbstractRelic makeCopy() {
        return new CentennialPuzzle();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * CentennialPuzzle.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


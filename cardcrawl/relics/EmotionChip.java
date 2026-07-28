package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.defect.ImpulseAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class EmotionChip
        extends AbstractRelic {
    public EmotionChip() {
        super("Emotion Chip", "emotionChip.png", RelicTier.RARE, LandingSound.CLINK);
        this.pulse = false;
    }
    public static final String ID = "Emotion Chip";

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void atTurnStart() {
        if (this.pulse) {
            this.pulse = false;
            flash();
            addToBot((AbstractGameAction) new ImpulseAction());
        }
    }

    public void wasHPLost(int damageAmount) {
        if ((AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT &&
                damageAmount > 0) {
            flash();
            if (!this.pulse) {
                beginPulse();
                this.pulse = true;
            }
        }
    }

    public void onVictory() {
        this.pulse = false;
    }

    public AbstractRelic makeCopy() {
        return new EmotionChip();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\EmotionChip
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


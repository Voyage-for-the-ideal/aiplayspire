package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class StoneCalendar
        extends AbstractRelic {
    public static final String ID = "StoneCalendar";

    public StoneCalendar() {
        super("StoneCalendar", "calendar.png", RelicTier.RARE, LandingSound.HEAVY);
    }
    private static final int TURNS = 7;
    private static final int DMG = 52;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\007' + this.DESCRIPTIONS[1] + '4' + this.DESCRIPTIONS[2];
    }

    public void atBattleStart() {
        this.counter = 0;
    }

    public void atTurnStart() {
        this.counter++;
        if (this.counter == 7) {
            beginLongPulse();
        }
    }

    public void onPlayerEndTurn() {
        if (this.counter == 7) {
            flash();
            addToBot(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
            addToBot((AbstractGameAction) new DamageAllEnemiesAction(null,

                    DamageInfo.createDamageMatrix(52, true), DamageInfo.DamageType.THORNS,
                    AbstractGameAction.AttackEffect.BLUNT_HEAVY));

            stopPulse();
            this.grayscale = true;
        }
    }

    public void justEnteredRoom(AbstractRoom room) {
        this.grayscale = false;
    }

    public void onVictory() {
        this.counter = -1;
        stopPulse();
    }

    public AbstractRelic makeCopy() {
        return new StoneCalendar();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * StoneCalendar.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


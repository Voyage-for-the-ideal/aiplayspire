package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Pocketwatch
        extends AbstractRelic {
    public static final String ID = "Pocketwatch";

    public Pocketwatch() {
        super("Pocketwatch", "pocketwatch.png", RelicTier.RARE, LandingSound.FLAT);
    }
    private static final int AMT = 3;
    private boolean firstTurn = true;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void atBattleStart() {
        this.counter = 0;
        this.firstTurn = true;
    }

    public void atTurnStartPostDraw() {
        if (this.counter <= 3 && !this.firstTurn) {
            addToBot((AbstractGameAction) new DrawCardAction((AbstractCreature) AbstractDungeon.player, 3));
        } else {
            this.firstTurn = false;
        }
        this.counter = 0;
        beginLongPulse();
    }

    public void onPlayCard(AbstractCard card, AbstractMonster m) {
        this.counter++;
        if (this.counter > 3) {
            stopPulse();
        }
    }

    public void onVictory() {
        this.counter = -1;
        stopPulse();
    }

    public AbstractRelic makeCopy() {
        return new Pocketwatch();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Pocketwatch
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


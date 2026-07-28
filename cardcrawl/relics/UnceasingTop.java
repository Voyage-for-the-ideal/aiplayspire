package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class UnceasingTop extends AbstractRelic {
    public static final String ID = "Unceasing Top";
    private boolean canDraw = false;
    private boolean disabledUntilEndOfTurn = false;

    public UnceasingTop() {
        super("Unceasing Top", "top.png", RelicTier.RARE, LandingSound.CLINK);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void atPreBattle() {
        this.canDraw = false;
    }

    public void atTurnStart() {
        this.canDraw = true;
        this.disabledUntilEndOfTurn = false;
    }

    public void disableUntilTurnEnds() {
        this.disabledUntilEndOfTurn = true;
    }

    public void onRefreshHand() {
        if (AbstractDungeon.actionManager.actions.isEmpty() && AbstractDungeon.player.hand.isEmpty()
                && !AbstractDungeon.actionManager.turnHasEnded && this.canDraw &&
                !AbstractDungeon.player.hasPower("No Draw") && !AbstractDungeon.isScreenUp) {
            if ((AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT && !this.disabledUntilEndOfTurn
                    && (AbstractDungeon.player.discardPile.size() > 0 || AbstractDungeon.player.drawPile.size() > 0)) {
                flash();
                addToTop((AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player,
                        this));
                addToBot((AbstractGameAction) new DrawCardAction((AbstractCreature) AbstractDungeon.player, 1));
            }
        }
    }

    public AbstractRelic makeCopy() {
        return new UnceasingTop();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * UnceasingTop.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


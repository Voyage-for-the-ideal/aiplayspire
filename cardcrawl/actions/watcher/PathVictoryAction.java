package com.megacrit.cardcrawl.actions.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.EmptyDeckShuffleAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.SoulGroup;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class PathVictoryAction
        extends AbstractGameAction {
    public PathVictoryAction() {
        if (AbstractDungeon.player.hasPower("No Draw")) {
            AbstractDungeon.player.getPower("No Draw").flash();
            setValues((AbstractCreature) AbstractDungeon.player, this.source, 1);
            this.isDone = true;
            this.duration = 0.0F;
            this.actionType = ActionType.WAIT;

            return;
        }
        setValues((AbstractCreature) AbstractDungeon.player, this.source, this.amount);
        this.actionType = ActionType.DRAW;

        if (Settings.FAST_MODE) {
            this.duration = Settings.ACTION_DUR_XFAST;
        } else {
            this.duration = Settings.ACTION_DUR_FASTER;
        }
    }

    public void update() {
        int deckSize = AbstractDungeon.player.drawPile.size();
        int discardSize = AbstractDungeon.player.discardPile.size();

        if (SoulGroup.isActive()) {
            return;
        }

        if (deckSize + discardSize == 0) {
            this.isDone = true;

            return;
        }
        if (AbstractDungeon.player.hand.size() == 10) {
            AbstractDungeon.player.createHandIsFullDialog();
            this.isDone = true;

            return;
        }
        if (deckSize == 0 && discardSize != 0) {
            addToTop(new PathVictoryAction());
            addToTop((AbstractGameAction) new EmptyDeckShuffleAction());
            this.isDone = true;

            return;
        }
        if (deckSize != 0) {
            AbstractCard c = AbstractDungeon.player.drawPile.getTopCard();
            c.setCostForTurn(0);
            AbstractDungeon.player.draw();
            AbstractDungeon.player.hand.refreshHandLayout();
            this.isDone = true;
            return;
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\watcher\
 * PathVictoryAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




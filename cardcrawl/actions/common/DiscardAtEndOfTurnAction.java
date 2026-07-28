package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.unique.RestoreRetainedCardsAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

public class DiscardAtEndOfTurnAction
        extends AbstractGameAction {
    private static final float DURATION = Settings.ACTION_DUR_XFAST;

    public DiscardAtEndOfTurnAction() {
        this.duration = DURATION;
    }

    public void update() {
        if (this.duration == DURATION) {

            for (Iterator<AbstractCard> c = AbstractDungeon.player.hand.group.iterator(); c.hasNext();) {
                AbstractCard e = c.next();
                if (e.retain || e.selfRetain) {
                    AbstractDungeon.player.limbo.addToTop(e);
                    c.remove();
                }
            }
            addToTop((AbstractGameAction) new RestoreRetainedCardsAction(AbstractDungeon.player.limbo));

            if (!AbstractDungeon.player.hasRelic("Runic Pyramid") && !AbstractDungeon.player.hasPower("Equilibrium")) {

                int tempSize = AbstractDungeon.player.hand.size();

                for (int i = 0; i < tempSize; i++) {
                    addToTop(new DiscardAction((AbstractCreature) AbstractDungeon.player, null,
                            AbstractDungeon.player.hand

                                    .size(),
                            true, true));
                }
            }

            ArrayList<AbstractCard> cards = (ArrayList<AbstractCard>) AbstractDungeon.player.hand.group.clone();
            Collections.shuffle(cards);
            for (AbstractCard abstractCard : cards) {
                abstractCard.triggerOnEndOfPlayerTurn();
            }
            this.isDone = true;
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * DiscardAtEndOfTurnAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




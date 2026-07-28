package com.megacrit.cardcrawl.actions.utility;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class ShowCardAction extends AbstractGameAction {
    private AbstractCard card = null;
    private static final float PURGE_DURATION = 0.2F;

    public ShowCardAction(AbstractCard card) {
        setValues((AbstractCreature) AbstractDungeon.player, null, 1);
        this.card = card;
        this.duration = 0.2F;
        this.actionType = ActionType.SPECIAL;
    }

    public void update() {
        if (this.duration == 0.2F) {

            if (AbstractDungeon.player.limbo.contains(this.card)) {
                AbstractDungeon.player.limbo.removeCard(this.card);
            }
            AbstractDungeon.player.cardInUse = null;
        }
        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\utility\
 * ShowCardAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




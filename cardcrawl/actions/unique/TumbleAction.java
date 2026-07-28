package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DiscardSpecificCardAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class TumbleAction
        extends AbstractGameAction {
    public TumbleAction(AbstractCreature source, int amount) {
        this.source = source;
        this.amount = amount;
        this.duration = Settings.ACTION_DUR_FAST;
    }

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {
            for (AbstractCard c : AbstractDungeon.player.hand.group) {
                if (c.type == AbstractCard.CardType.ATTACK) {
                    addToTop((AbstractGameAction) new GainBlockAction(this.source, this.source, this.amount));
                    addToTop((AbstractGameAction) new DiscardSpecificCardAction(c));
                }
            }
        }
        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * TumbleAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




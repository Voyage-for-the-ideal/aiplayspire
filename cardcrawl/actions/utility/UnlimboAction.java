package com.megacrit.cardcrawl.actions.utility;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.cardManip.ExhaustCardEffect;

public class UnlimboAction extends AbstractGameAction {
    private AbstractCard card;
    private boolean exhaust;

    public UnlimboAction(AbstractCard card, boolean exhaust) {
        this.duration = Settings.ACTION_DUR_XFAST;
        this.card = card;
        this.exhaust = exhaust;
    }

    public UnlimboAction(AbstractCard card) {
        this(card, false);
    }

    public void update() {
        if (this.duration == Settings.ACTION_DUR_XFAST) {
            if (!this.exhaust)
                ;

            AbstractDungeon.player.limbo.removeCard(this.card);
            if (this.exhaust) {
                AbstractDungeon.effectList.add(new ExhaustCardEffect(this.card));
            }
            this.isDone = true;
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\utility\
 * UnlimboAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




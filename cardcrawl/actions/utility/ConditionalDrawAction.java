package com.megacrit.cardcrawl.actions.utility;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class ConditionalDrawAction extends AbstractGameAction {
    private AbstractCard.CardType restrictedType;

    public ConditionalDrawAction(int newAmount, AbstractCard.CardType restrictedType) {
        this.duration = Settings.ACTION_DUR_FAST;
        this.actionType = ActionType.WAIT;
        this.source = (AbstractCreature) AbstractDungeon.player;
        this.target = (AbstractCreature) AbstractDungeon.player;
        this.amount = newAmount;
        this.restrictedType = restrictedType;
    }

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {

            if (checkCondition()) {
                addToTop((AbstractGameAction) new DrawCardAction(this.source, this.amount));
            }

            this.isDone = true;
        }
    }

    private boolean checkCondition() {
        for (AbstractCard c : AbstractDungeon.player.hand.group) {
            if (c.type == this.restrictedType) {
                return false;
            }
        }

        return true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\utility\
 * ConditionalDrawAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




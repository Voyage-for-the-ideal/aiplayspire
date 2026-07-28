package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class DiscardSpecificCardAction
        extends AbstractGameAction {
    private AbstractCard targetCard;
    private CardGroup group;

    public DiscardSpecificCardAction(AbstractCard targetCard) {
        this.targetCard = targetCard;
        this.actionType = ActionType.DISCARD;
        this.duration = Settings.ACTION_DUR_FAST;
    }

    public DiscardSpecificCardAction(AbstractCard targetCard, CardGroup group) {
        this.targetCard = targetCard;
        this.group = group;
        this.actionType = ActionType.DISCARD;
        this.duration = Settings.ACTION_DUR_FAST;
    }

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {
            if (this.group == null) {
                this.group = AbstractDungeon.player.hand;
            }
            if (this.group.contains(this.targetCard)) {
                this.group.moveToDiscardPile(this.targetCard);
                GameActionManager.incrementDiscard(false);
                this.targetCard.triggerOnManualDiscard();
            }
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * DiscardSpecificCardAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




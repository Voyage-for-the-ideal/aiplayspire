package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;

public class UpgradeSpecificCardAction extends AbstractGameAction {
    private AbstractCard c;

    public UpgradeSpecificCardAction(AbstractCard cardToUpgrade) {
        this.actionType = ActionType.CARD_MANIPULATION;
        this.c = cardToUpgrade;
        this.duration = Settings.ACTION_DUR_FAST;
    }

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {
            if (this.c.canUpgrade() && this.c.type != AbstractCard.CardType.STATUS) {
                this.c.upgrade();
                this.c.superFlash();
                this.c.applyPowers();
            }

            this.isDone = true;

            return;
        }
        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\common\
 * UpgradeSpecificCardAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




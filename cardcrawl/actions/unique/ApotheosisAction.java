package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class ApotheosisAction
        extends AbstractGameAction {
    public void update() {
        if (this.duration == Settings.ACTION_DUR_MED) {
            AbstractPlayer p = AbstractDungeon.player;

            upgradeAllCardsInGroup(p.hand);
            upgradeAllCardsInGroup(p.drawPile);
            upgradeAllCardsInGroup(p.discardPile);
            upgradeAllCardsInGroup(p.exhaustPile);

            this.isDone = true;
        }
    }

    private void upgradeAllCardsInGroup(CardGroup cardGroup) {
        for (AbstractCard c : cardGroup.group) {
            if (c.canUpgrade()) {
                if (cardGroup.type == CardGroup.CardGroupType.HAND) {
                    c.superFlash();
                }
                c.upgrade();
                c.applyPowers();
            }
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * ApotheosisAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




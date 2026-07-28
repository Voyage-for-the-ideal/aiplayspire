package com.megacrit.cardcrawl.actions.common;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class UpgradeRandomCardAction
        extends AbstractGameAction {
    private AbstractPlayer p = AbstractDungeon.player;

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {

            if (this.p.hand.group.size() <= 0) {
                this.isDone = true;

                return;
            }
            CardGroup upgradeable = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);

            for (AbstractCard c : this.p.hand.group) {
                if (c.canUpgrade() && c.type != AbstractCard.CardType.STATUS) {
                    upgradeable.addToTop(c);
                }
            }

            if (upgradeable.size() > 0) {
                upgradeable.shuffle();
                ((AbstractCard) upgradeable.group.get(0)).upgrade();
                ((AbstractCard) upgradeable.group.get(0)).superFlash();
                ((AbstractCard) upgradeable.group.get(0)).applyPowers();
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
 * UpgradeRandomCardAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




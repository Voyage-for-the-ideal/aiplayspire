package com.megacrit.cardcrawl.actions.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class DEPRECATEDExperiencedAction extends AbstractGameAction {
    private int blockPerCard;

    public DEPRECATEDExperiencedAction(int blockPerCard, AbstractCard card) {
        this.blockPerCard = blockPerCard;
        this.card = card;
    }
    private AbstractCard card;

    public void update() {
        int upgradeCount = 0;
        for (AbstractCard c : AbstractDungeon.player.hand.group) {
            if (c.upgraded && c != this.card) {
                upgradeCount++;
            }
        }
        addToTop((AbstractGameAction) new GainBlockAction((AbstractCreature) AbstractDungeon.player,
                (AbstractCreature) AbstractDungeon.player, upgradeCount * this.blockPerCard));

        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\deprecated
 * \DEPRECATEDExperiencedAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




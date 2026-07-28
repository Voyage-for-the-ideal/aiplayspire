package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInHandAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.unlock.UnlockTracker;

public class Enchiridion extends AbstractRelic {
    public static final String ID = "Enchiridion";

    public Enchiridion() {
        super("Enchiridion", "enchiridion.png", RelicTier.SPECIAL, LandingSound.FLAT);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void atPreBattle() {
        flash();
        AbstractCard c = AbstractDungeon.returnTrulyRandomCardInCombat(AbstractCard.CardType.POWER).makeCopy();
        if (c.cost != -1) {
            c.setCostForTurn(0);
        }
        UnlockTracker.markCardAsSeen(c.cardID);
        addToBot((AbstractGameAction) new MakeTempCardInHandAction(c));
    }

    public AbstractRelic makeCopy() {
        return new Enchiridion();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Enchiridion
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInHandAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class DeadBranch extends AbstractRelic {
    public DeadBranch() {
        super("Dead Branch", "deadBranch.png", RelicTier.RARE, LandingSound.FLAT);
    }

    public void onExhaust(AbstractCard card) {
        if (!AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
            flash();
            addToBot(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
            addToBot((AbstractGameAction) new MakeTempCardInHandAction(
                    AbstractDungeon.returnTrulyRandomCardInCombat().makeCopy(), false));
        }
    }
    public static final String ID = "Dead Branch";

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public AbstractRelic makeCopy() {
        return new DeadBranch();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\DeadBranch.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


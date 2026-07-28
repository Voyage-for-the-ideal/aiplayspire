package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class InkBottle extends AbstractRelic {
    public static final String ID = "InkBottle";

    public InkBottle() {
        super("InkBottle", "ink_bottle.png", RelicTier.UNCOMMON, LandingSound.CLINK);
        this.counter = 0;
    }
    private static final int COUNT = 10;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        this.counter++;

        if (this.counter == 10) {
            this.counter = 0;
            flash();
            this.pulse = false;
            addToBot(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
            addToBot((AbstractGameAction) new DrawCardAction(1));
        } else if (this.counter == 9) {
            beginPulse();
            this.pulse = true;
        }
    }

    public void atBattleStart() {
        if (this.counter == 9) {
            beginPulse();
            this.pulse = true;
        }
    }

    public AbstractRelic makeCopy() {
        return new InkBottle();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\InkBottle.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


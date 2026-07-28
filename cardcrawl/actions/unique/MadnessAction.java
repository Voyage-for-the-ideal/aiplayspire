package com.megacrit.cardcrawl.actions.unique;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class MadnessAction
        extends AbstractGameAction {
    private AbstractPlayer p = AbstractDungeon.player;

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {
            boolean betterPossible = false;
            boolean possible = false;
            for (AbstractCard c : this.p.hand.group) {
                if (c.costForTurn > 0) {
                    betterPossible = true;
                    continue;
                }
                if (c.cost > 0) {
                    possible = true;
                }
            }
            if (betterPossible || possible) {
                findAndModifyCard(betterPossible);
            }
        }

        tickDuration();
    }

    private void findAndModifyCard(boolean better) {
        AbstractCard c = this.p.hand.getRandomCard(AbstractDungeon.cardRandomRng);
        if (better) {
            if (c.costForTurn > 0) {

                c.cost = 0;
                c.costForTurn = 0;
                c.isCostModified = true;
                c.superFlash(Color.GOLD.cpy());
            } else {

                findAndModifyCard(better);
            }

        } else if (c.cost > 0) {

            c.cost = 0;
            c.costForTurn = 0;
            c.isCostModified = true;
            c.superFlash(Color.GOLD.cpy());
        } else {

            findAndModifyCard(better);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * MadnessAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




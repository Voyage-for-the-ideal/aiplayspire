package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;

public class RecycleAction extends AbstractGameAction {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("RecycleAction");
    public static final String[] TEXT = uiStrings.TEXT;

    private AbstractPlayer p = AbstractDungeon.player;

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {
            if (this.p.hand.isEmpty()) {
                this.isDone = true;
                return;
            }
            if (this.p.hand.size() == 1) {
                if ((this.p.hand.getBottomCard()).costForTurn == -1) {
                    addToTop((AbstractGameAction) new GainEnergyAction(EnergyPanel.getCurrentEnergy()));
                } else if ((this.p.hand.getBottomCard()).costForTurn > 0) {
                    addToTop((AbstractGameAction) new GainEnergyAction((this.p.hand.getBottomCard()).costForTurn));
                }
                this.p.hand.moveToExhaustPile(this.p.hand.getBottomCard());
                tickDuration();
                return;
            }
            AbstractDungeon.handCardSelectScreen.open(TEXT[0], 1, false);
            tickDuration();

            return;
        }

        if (!AbstractDungeon.handCardSelectScreen.wereCardsRetrieved) {
            for (AbstractCard c : AbstractDungeon.handCardSelectScreen.selectedCards.group) {
                if (c.costForTurn == -1) {
                    addToTop((AbstractGameAction) new GainEnergyAction(EnergyPanel.getCurrentEnergy()));
                } else if (c.costForTurn > 0) {
                    addToTop((AbstractGameAction) new GainEnergyAction(c.costForTurn));
                }
                this.p.hand.moveToExhaustPile(c);
            }
            AbstractDungeon.handCardSelectScreen.wereCardsRetrieved = true;
            AbstractDungeon.handCardSelectScreen.selectedCards.group.clear();
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * RecycleAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




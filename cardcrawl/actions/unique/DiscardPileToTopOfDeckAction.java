package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.UIStrings;

public class DiscardPileToTopOfDeckAction extends AbstractGameAction {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("DiscardPileToTopOfDeckAction");
    public static final String[] TEXT = uiStrings.TEXT;
    private AbstractPlayer p;

    public DiscardPileToTopOfDeckAction(AbstractCreature source) {
        this.p = AbstractDungeon.player;
        setValues(null, source, this.amount);
        this.actionType = ActionType.CARD_MANIPULATION;
        this.duration = Settings.ACTION_DUR_FASTER;
    }

    public void update() {
        if (AbstractDungeon.getCurrRoom().isBattleEnding()) {
            this.isDone = true;
            return;
        }
        if (this.duration == Settings.ACTION_DUR_FASTER) {
            if (this.p.discardPile.isEmpty()) {
                this.isDone = true;
                return;
            }
            if (this.p.discardPile.size() == 1) {
                AbstractCard tmp = this.p.discardPile.getTopCard();
                this.p.discardPile.removeCard(tmp);
                this.p.discardPile.moveToDeck(tmp, false);
            }

            if (this.p.discardPile.group.size() > this.amount) {
                AbstractDungeon.gridSelectScreen.open(this.p.discardPile, 1, TEXT[0], false, false, false, false);
                tickDuration();

                return;
            }
        }

        if (!AbstractDungeon.gridSelectScreen.selectedCards.isEmpty()) {
            for (AbstractCard c : AbstractDungeon.gridSelectScreen.selectedCards) {
                this.p.discardPile.removeCard(c);
                this.p.hand.moveToDeck(c, false);
            }
            AbstractDungeon.gridSelectScreen.selectedCards.clear();
            AbstractDungeon.player.hand.refreshHandLayout();
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * DiscardPileToTopOfDeckAction.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




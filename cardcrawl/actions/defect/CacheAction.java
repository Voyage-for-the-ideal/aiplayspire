package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.UIStrings;

public class CacheAction extends AbstractGameAction {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("CacheAction");
    public static final String[] TEXT = uiStrings.TEXT;
    private AbstractPlayer p;

    public CacheAction(int amount) {
        this.p = AbstractDungeon.player;
        setValues((AbstractCreature) this.p, (AbstractCreature) AbstractDungeon.player, amount);
        this.actionType = ActionType.CARD_MANIPULATION;
        this.duration = Settings.ACTION_DUR_MED;
    }

    public void update() {
        if (this.duration == Settings.ACTION_DUR_MED) {
            if (AbstractDungeon.player.drawPile.size() <= 1) {
                this.isDone = true;

                return;
            }
            if (this.amount == 1) {
                AbstractDungeon.gridSelectScreen.open(AbstractDungeon.player.drawPile, this.amount, TEXT[0], false);
            } else {
                if (AbstractDungeon.player.drawPile.size() > this.amount) {
                    this.amount = AbstractDungeon.player.drawPile.size();
                }
                AbstractDungeon.gridSelectScreen.open(AbstractDungeon.player.drawPile, this.amount, TEXT[1], false);
            }
            tickDuration();

            return;
        }

        if (AbstractDungeon.gridSelectScreen.selectedCards.size() != 0) {
            for (int i = AbstractDungeon.gridSelectScreen.selectedCards.size() - 1; i > -1; i--) {
                ((AbstractCard) AbstractDungeon.gridSelectScreen.selectedCards.get(i)).unhover();
                this.p.drawPile.moveToDeck(AbstractDungeon.gridSelectScreen.selectedCards.get(i), false);
            }
            AbstractDungeon.gridSelectScreen.selectedCards.clear();
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * CacheAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




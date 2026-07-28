package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.NightmarePower;

public class NightmareAction extends AbstractGameAction {
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString("CopyAction");
    public static final String[] TEXT = uiStrings.TEXT;

    private AbstractPlayer p;
    public static int numDiscarded;
    private static final float DURATION = Settings.ACTION_DUR_XFAST;

    public NightmareAction(AbstractCreature target, AbstractCreature source, int amount) {
        setValues(target, source, amount);
        this.actionType = ActionType.CARD_MANIPULATION;
        this.duration = DURATION;
        this.p = (AbstractPlayer) target;
    }

    public void update() {
        if (this.duration == DURATION) {
            if (this.p.hand.isEmpty()) {
                this.isDone = true;
                return;
            }
            if (this.p.hand.size() == 1) {
                addToTop((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this.p, (AbstractCreature) this.p,
                        (AbstractPower) new NightmarePower((AbstractCreature) this.p, this.amount,
                                this.p.hand.getBottomCard())));
                this.isDone = true;
                return;
            }
            AbstractDungeon.handCardSelectScreen.open(TEXT[0], 1, false, false);
            tickDuration();

            return;
        }

        if (!AbstractDungeon.handCardSelectScreen.wereCardsRetrieved) {
            AbstractCard tmpCard = AbstractDungeon.handCardSelectScreen.selectedCards.getBottomCard();
            addToTop((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this.p, (AbstractCreature) this.p,
                    (AbstractPower) new NightmarePower((AbstractCreature) this.p, this.amount, tmpCard)));
            AbstractDungeon.player.hand.addToHand(tmpCard);

            AbstractDungeon.handCardSelectScreen.selectedCards.clear();
            AbstractDungeon.handCardSelectScreen.wereCardsRetrieved = true;
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * NightmareAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




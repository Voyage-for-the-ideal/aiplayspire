package com.megacrit.cardcrawl.actions.defect;

import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.common.EmptyDeckShuffleAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.SoulGroup;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.vfx.PlayerTurnEffect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

@Deprecated
public class ScrapeAction
        extends AbstractGameAction {
    private boolean shuffleCheck = false;
    private static final Logger logger = LogManager.getLogger(DrawCardAction.class.getName());
    public static ArrayList<AbstractCard> scrapedCards = new ArrayList<>();

    public ScrapeAction(AbstractCreature source, int amount, boolean endTurnDraw) {
        if (endTurnDraw) {
            AbstractDungeon.topLevelEffects.add(new PlayerTurnEffect());
        } else if (AbstractDungeon.player.hasPower("No Draw")) {
            AbstractDungeon.player.getPower("No Draw").flash();
            setValues((AbstractCreature) AbstractDungeon.player, source, amount);
            this.isDone = true;
            this.duration = 0.0F;
            this.actionType = ActionType.WAIT;

            return;
        }
        setValues((AbstractCreature) AbstractDungeon.player, source, amount);
        this.actionType = ActionType.DRAW;
        if (Settings.FAST_MODE) {
            this.duration = Settings.ACTION_DUR_XFAST;
        } else {
            this.duration = Settings.ACTION_DUR_FASTER;
        }
    }

    public ScrapeAction(AbstractCreature source, int amount) {
        this(source, amount, false);
    }

    public void update() {
        if (this.amount <= 0) {
            this.isDone = true;

            return;
        }
        int deckSize = AbstractDungeon.player.drawPile.size();
        int discardSize = AbstractDungeon.player.discardPile.size();

        if (SoulGroup.isActive()) {
            return;
        }

        if (deckSize + discardSize == 0) {
            this.isDone = true;

            return;
        }
        if (AbstractDungeon.player.hand.size() == 10) {
            AbstractDungeon.player.createHandIsFullDialog();
            this.isDone = true;

            return;
        }

        if (!this.shuffleCheck) {
            if (this.amount > deckSize) {
                int tmp = this.amount - deckSize;
                addToTop(new ScrapeAction((AbstractCreature) AbstractDungeon.player, tmp));
                addToTop((AbstractGameAction) new EmptyDeckShuffleAction());
                if (deckSize != 0) {
                    addToTop(new ScrapeAction((AbstractCreature) AbstractDungeon.player, deckSize));
                }
                this.amount = 0;
                this.isDone = true;
            }
            this.shuffleCheck = true;
        }

        this.duration -= Gdx.graphics.getDeltaTime();

        if (this.amount != 0 && this.duration < 0.0F) {
            if (Settings.FAST_MODE) {
                this.duration = Settings.ACTION_DUR_XFAST;
            } else {
                this.duration = Settings.ACTION_DUR_FASTER;
            }
            this.amount--;

            if (!AbstractDungeon.player.drawPile.isEmpty()) {
                scrapedCards.add(AbstractDungeon.player.drawPile.getTopCard());
                AbstractDungeon.player.draw();
                AbstractDungeon.player.hand.refreshHandLayout();
            } else {
                logger.warn("Player attempted to draw from an empty drawpile mid-DrawAction?MASTER DECK: "
                        + AbstractDungeon.player.masterDeck

                                .getCardNames());
                this.isDone = true;
            }

            if (this.amount == 0)
                this.isDone = true;
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * ScrapeAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




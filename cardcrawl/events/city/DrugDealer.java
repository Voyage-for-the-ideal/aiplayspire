package com.megacrit.cardcrawl.events.city;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.colorless.JAX;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractImageEvent;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.localization.EventStrings;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.relics.Circlet;
import com.megacrit.cardcrawl.relics.MutagenicStrength;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DrugDealer
        extends AbstractImageEvent {
    private static final Logger logger = LogManager.getLogger(DrugDealer.class.getName());
    public static final String ID = "Drug Dealer";
    private static final EventStrings eventStrings = CardCrawlGame.languagePack.getEventString("Drug Dealer");
    public static final String NAME = eventStrings.NAME;
    public static final String[] DESCRIPTIONS = eventStrings.DESCRIPTIONS;
    public static final String[] OPTIONS = eventStrings.OPTIONS;

    private int screenNum = 0;
    private boolean cardsSelected = false;

    public DrugDealer() {
        super(NAME, DESCRIPTIONS[0], "images/events/drugDealer.jpg");

        this.imageEventText.setDialogOption(OPTIONS[0], CardLibrary.getCopy("J.A.X."));
        if (AbstractDungeon.player.masterDeck.getPurgeableCards().size() >= 2) {
            this.imageEventText.setDialogOption(OPTIONS[1]);
        } else {
            this.imageEventText.setDialogOption(OPTIONS[4], true);
        }
        this.imageEventText.setDialogOption(OPTIONS[2], (AbstractRelic) new MutagenicStrength());
    }
    protected void buttonEffect(int buttonPressed) {
        JAX jAX;
        Circlet circlet = null;
        switch (this.screenNum) {
            case 0:
                switch (buttonPressed) {
                    case 0:
                        jAX = new JAX();
                        logMetricObtainCard("Drug Dealer", "Obtain J.A.X.", (AbstractCard) jAX);
                        this.imageEventText.updateBodyText(DESCRIPTIONS[1]);
                        AbstractDungeon.effectList.add(new ShowCardAndObtainEffect((AbstractCard) jAX,
                                (Settings.WIDTH / 2), (Settings.HEIGHT / 2)));

                        this.imageEventText.updateDialogOption(0, OPTIONS[3]);
                        this.imageEventText.clearRemainingOptions();
                        break;
                    case 1:
                        this.imageEventText.updateBodyText(DESCRIPTIONS[2]);
                        transform();
                        this.imageEventText.updateDialogOption(0, OPTIONS[3]);
                        this.imageEventText.clearRemainingOptions();
                        break;
                    case 2:
                        this.imageEventText.updateBodyText(DESCRIPTIONS[3]);

                        if (!AbstractDungeon.player.hasRelic("MutagenicStrength")) {
                            MutagenicStrength mutagenicStrength = new MutagenicStrength();
                            AbstractDungeon.getCurrRoom().spawnRelicAndObtain(this.drawX, this.drawY,
                                    (AbstractRelic) mutagenicStrength);
                        } else {
                            circlet = new Circlet();
                            AbstractDungeon.getCurrRoom().spawnRelicAndObtain(this.drawX, this.drawY,
                                    (AbstractRelic) circlet);
                        }
                        logMetricObtainRelic("Drug Dealer", "Inject Mutagens", (AbstractRelic) circlet);
                        this.imageEventText.updateDialogOption(0, OPTIONS[3]);
                        this.imageEventText.clearRemainingOptions();
                        break;
                    default:
                        logger.info("ERROR: Unhandled case " + buttonPressed);
                        break;
                }
                this.screenNum = 1;
                break;
            case 1:
                openMap();
                break;
        }
    }

    public void update() {
        super.update();
        if (!this.cardsSelected) {
            List<String> transformedCards = new ArrayList<>();
            List<String> obtainedCards = new ArrayList<>();
            if (AbstractDungeon.gridSelectScreen.selectedCards.size() == 2) {
                this.cardsSelected = true;
                float displayCount = 0.0F;
                Iterator<AbstractCard> i = AbstractDungeon.gridSelectScreen.selectedCards.iterator();
                while (i.hasNext()) {
                    AbstractCard card = i.next();
                    card.untip();
                    card.unhover();
                    transformedCards.add(card.cardID);
                    AbstractDungeon.player.masterDeck.removeCard(card);
                    AbstractDungeon.transformCard(card, false, AbstractDungeon.miscRng);

                    AbstractCard c = AbstractDungeon.getTransformedCard();
                    obtainedCards.add(c.cardID);

                    if (AbstractDungeon.screen != AbstractDungeon.CurrentScreen.TRANSFORM && c != null) {
                        AbstractDungeon.topLevelEffectsQueue.add(new ShowCardAndObtainEffect(c

                                .makeCopy(), Settings.WIDTH / 3.0F + displayCount, Settings.HEIGHT / 2.0F, false));

                        displayCount += Settings.WIDTH / 6.0F;
                    }
                }
                AbstractDungeon.gridSelectScreen.selectedCards.clear();
                logMetricTransformCards("Drug Dealer", "Became Test Subject", transformedCards, obtainedCards);
                (AbstractDungeon.getCurrRoom()).rewardPopOutTimer = 0.25F;
            }
        }
    }

    private void transform() {
        if (!AbstractDungeon.isScreenUp) {
            AbstractDungeon.gridSelectScreen.open(AbstractDungeon.player.masterDeck
                    .getPurgeableCards(), 2, OPTIONS[5], false, false, false, false);

        } else {

            AbstractDungeon.dynamicBanner.hide();
            AbstractDungeon.previousScreen = AbstractDungeon.screen;
            AbstractDungeon.gridSelectScreen.open(AbstractDungeon.player.masterDeck
                    .getPurgeableCards(), 2, OPTIONS[5], false, false, false, false);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\events\city\
 * DrugDealer.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.LocalizedStrings;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;

import java.util.ArrayList;
import java.util.Iterator;

public class Astrolabe
        extends AbstractRelic {
    public static final String ID = "Astrolabe";
    private boolean cardsSelected = true;

    public Astrolabe() {
        super("Astrolabe", "astrolabe.png", RelicTier.BOSS, LandingSound.CLINK);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void onEquip() {
        this.cardsSelected = false;
        CardGroup tmp = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);
        for (AbstractCard card : (AbstractDungeon.player.masterDeck.getPurgeableCards()).group) {
            tmp.addToTop(card);
        }

        if (tmp.group.isEmpty()) {
            this.cardsSelected = true;
            return;
        }
        if (tmp.group.size() <= 3) {
            giveCards(tmp.group);
        } else if (!AbstractDungeon.isScreenUp) {
            AbstractDungeon.gridSelectScreen.open(tmp, 3, this.DESCRIPTIONS[1] + this.name + LocalizedStrings.PERIOD,
                    false, false, false, false);

        } else {

            AbstractDungeon.dynamicBanner.hide();
            AbstractDungeon.previousScreen = AbstractDungeon.screen;
            AbstractDungeon.gridSelectScreen.open(tmp, 3, this.DESCRIPTIONS[1] + this.name + LocalizedStrings.PERIOD,
                    false, false, false, false);
        }
    }

    public void update() {
        super.update();
        if (!this.cardsSelected &&
                AbstractDungeon.gridSelectScreen.selectedCards.size() == 3) {
            giveCards(AbstractDungeon.gridSelectScreen.selectedCards);
        }
    }

    public void giveCards(ArrayList<AbstractCard> group) {
        this.cardsSelected = true;
        float displayCount = 0.0F;
        for (Iterator<AbstractCard> i = group.iterator(); i.hasNext();) {
            AbstractCard card = i.next();
            card.untip();
            card.unhover();
            AbstractDungeon.player.masterDeck.removeCard(card);
            AbstractDungeon.transformCard(card, true, AbstractDungeon.miscRng);

            if (AbstractDungeon.screen != AbstractDungeon.CurrentScreen.TRANSFORM
                    && AbstractDungeon.transformedCard != null) {

                AbstractDungeon.topLevelEffectsQueue.add(new ShowCardAndObtainEffect(

                        AbstractDungeon.getTransformedCard(), Settings.WIDTH / 3.0F + displayCount,
                        Settings.HEIGHT / 2.0F, false));

                displayCount += Settings.WIDTH / 6.0F;
            }
        }
        AbstractDungeon.gridSelectScreen.selectedCards.clear();
        (AbstractDungeon.getCurrRoom()).rewardPopOutTimer = 0.25F;
    }

    public AbstractRelic makeCopy() {
        return new Astrolabe();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Astrolabe.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


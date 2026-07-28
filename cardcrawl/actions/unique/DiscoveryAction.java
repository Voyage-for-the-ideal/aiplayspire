package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.screens.CardRewardScreen;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndAddToDiscardEffect;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndAddToHandEffect;

import java.util.ArrayList;

public class DiscoveryAction
        extends AbstractGameAction {
    private boolean retrieveCard = false;
    private boolean returnColorless = false;
    private AbstractCard.CardType cardType = null;

    public DiscoveryAction() {
        this.actionType = ActionType.CARD_MANIPULATION;
        this.duration = Settings.ACTION_DUR_FAST;
        this.amount = 1;
    }

    public DiscoveryAction(AbstractCard.CardType type, int amount) {
        this.actionType = ActionType.CARD_MANIPULATION;
        this.duration = Settings.ACTION_DUR_FAST;
        this.amount = amount;
        this.cardType = type;
    }

    public DiscoveryAction(boolean colorless, int amount) {
        this.actionType = ActionType.CARD_MANIPULATION;
        this.duration = Settings.ACTION_DUR_FAST;
        this.amount = amount;
        this.returnColorless = colorless;
    }

    public void update() {
        ArrayList<AbstractCard> generatedCards;
        if (this.returnColorless) {
            generatedCards = generateColorlessCardChoices();
        } else {
            generatedCards = generateCardChoices(this.cardType);
        }

        if (this.duration == Settings.ACTION_DUR_FAST) {
            AbstractDungeon.cardRewardScreen.customCombatOpen(generatedCards, CardRewardScreen.TEXT[1],
                    (this.cardType != null));

            tickDuration();

            return;
        }
        if (!this.retrieveCard) {
            if (AbstractDungeon.cardRewardScreen.discoveryCard != null) {
                AbstractCard disCard = AbstractDungeon.cardRewardScreen.discoveryCard.makeStatEquivalentCopy();
                AbstractCard disCard2 = AbstractDungeon.cardRewardScreen.discoveryCard.makeStatEquivalentCopy();

                if (AbstractDungeon.player.hasPower("MasterRealityPower")) {
                    disCard.upgrade();
                    disCard2.upgrade();
                }

                disCard.setCostForTurn(0);
                disCard2.setCostForTurn(0);

                disCard.current_x = -1000.0F * Settings.xScale;
                disCard2.current_x = -1000.0F * Settings.xScale + AbstractCard.IMG_HEIGHT_S;

                if (this.amount == 1) {
                    if (AbstractDungeon.player.hand.size() < 10) {
                        AbstractDungeon.effectList.add(
                                new ShowCardAndAddToHandEffect(disCard, Settings.WIDTH / 2.0F, Settings.HEIGHT / 2.0F));
                    } else {

                        AbstractDungeon.effectList.add(new ShowCardAndAddToDiscardEffect(disCard, Settings.WIDTH / 2.0F,
                                Settings.HEIGHT / 2.0F));
                    }

                    disCard2 = null;
                } else if (AbstractDungeon.player.hand.size() + this.amount <= 10) {
                    AbstractDungeon.effectList.add(new ShowCardAndAddToHandEffect(disCard,
                            Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH / 2.0F, Settings.HEIGHT / 2.0F));

                    AbstractDungeon.effectList.add(new ShowCardAndAddToHandEffect(disCard2,
                            Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH / 2.0F, Settings.HEIGHT / 2.0F));

                } else if (AbstractDungeon.player.hand.size() == 9) {
                    AbstractDungeon.effectList.add(new ShowCardAndAddToHandEffect(disCard,
                            Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH / 2.0F, Settings.HEIGHT / 2.0F));

                    AbstractDungeon.effectList.add(new ShowCardAndAddToDiscardEffect(disCard2,
                            Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH / 2.0F, Settings.HEIGHT / 2.0F));

                } else {

                    AbstractDungeon.effectList.add(new ShowCardAndAddToDiscardEffect(disCard,
                            Settings.WIDTH / 2.0F - AbstractCard.IMG_WIDTH / 2.0F, Settings.HEIGHT / 2.0F));

                    AbstractDungeon.effectList.add(new ShowCardAndAddToDiscardEffect(disCard2,
                            Settings.WIDTH / 2.0F + AbstractCard.IMG_WIDTH / 2.0F, Settings.HEIGHT / 2.0F));
                }

                AbstractDungeon.cardRewardScreen.discoveryCard = null;
            }
            this.retrieveCard = true;
        }

        tickDuration();
    }

    private ArrayList<AbstractCard> generateColorlessCardChoices() {
        ArrayList<AbstractCard> derp = new ArrayList<>();

        while (derp.size() != 3) {
            boolean dupe = false;

            AbstractCard tmp = AbstractDungeon.returnTrulyRandomColorlessCardInCombat();
            for (AbstractCard c : derp) {
                if (c.cardID.equals(tmp.cardID)) {
                    dupe = true;
                    break;
                }
            }
            if (!dupe) {
                derp.add(tmp.makeCopy());
            }
        }

        return derp;
    }

    private ArrayList<AbstractCard> generateCardChoices(AbstractCard.CardType type) {
        ArrayList<AbstractCard> derp = new ArrayList<>();

        while (derp.size() != 3) {
            boolean dupe = false;
            AbstractCard tmp = null;
            if (type == null) {
                tmp = AbstractDungeon.returnTrulyRandomCardInCombat();
            } else {
                tmp = AbstractDungeon.returnTrulyRandomCardInCombat(type);
            }
            for (AbstractCard c : derp) {
                if (c.cardID.equals(tmp.cardID)) {
                    dupe = true;
                    break;
                }
            }
            if (!dupe) {
                derp.add(tmp.makeCopy());
            }
        }

        return derp;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * DiscoveryAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




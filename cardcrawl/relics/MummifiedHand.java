package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class MummifiedHand
        extends AbstractRelic {
    private static final Logger logger = LogManager.getLogger(MummifiedHand.class.getName());
    public static final String ID = "Mummified Hand";

    public MummifiedHand() {
        super("Mummified Hand", "mummifiedHand.png", RelicTier.UNCOMMON, LandingSound.FLAT);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (card.type == AbstractCard.CardType.POWER) {
            flash();
            addToTop(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));

            ArrayList<AbstractCard> groupCopy = new ArrayList<>();
            for (AbstractCard abstractCard : AbstractDungeon.player.hand.group) {
                if (abstractCard.cost > 0 && abstractCard.costForTurn > 0 && !abstractCard.freeToPlayOnce) {
                    groupCopy.add(abstractCard);
                    continue;
                }
                logger.info("COST IS 0: " + abstractCard.name);
            }

            for (CardQueueItem i : AbstractDungeon.actionManager.cardQueue) {
                if (i.card != null) {
                    logger.info("INVALID: " + i.card.name);
                    groupCopy.remove(i.card);
                }
            }

            AbstractCard c = null;
            if (!groupCopy.isEmpty()) {
                logger.info("VALID CARDS: ");
                for (AbstractCard cc : groupCopy) {
                    logger.info(cc.name);
                }

                c = groupCopy.get(AbstractDungeon.cardRandomRng.random(0, groupCopy.size() - 1));
            } else {
                logger.info("NO VALID CARDS");
            }

            if (c != null) {
                logger.info("Mummified hand: " + c.name);
                c.setCostForTurn(0);
            } else {
                logger.info("ERROR: MUMMIFIED HAND NOT WORKING");
            }
        }
    }

    public AbstractRelic makeCopy() {
        return new MummifiedHand();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * MummifiedHand.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


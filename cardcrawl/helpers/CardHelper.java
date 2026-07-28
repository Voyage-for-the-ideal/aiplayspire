package com.megacrit.cardcrawl.helpers;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.unlock.UnlockTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;

public class CardHelper {
    private static final Logger logger = LogManager.getLogger(CardHelper.class.getName());

    public static final int COMMON_CARD_LIMIT = 3;
    public static final int UNCOMMON_CARD_LIMIT = 2;
    public static HashMap<String, Integer> obtainedCards = new HashMap<>();
    public static ArrayList<CardInfo> removedCards = new ArrayList<>();

    public static void obtain(String key, AbstractCard.CardRarity rarity, AbstractCard.CardColor color) {
        if (rarity == AbstractCard.CardRarity.SPECIAL || rarity == AbstractCard.CardRarity.BASIC
                || rarity == AbstractCard.CardRarity.CURSE) {
            logger.info("No need to track rarity type: " + rarity.name());

            return;
        }
        if (obtainedCards.containsKey(key)) {
            int tmp = ((Integer) obtainedCards.get(key)).intValue() + 1;
            obtainedCards.put(key, Integer.valueOf(tmp));
            logger.info("Obtained " + key + " (" + rarity.name() + "). You have " + tmp + " now");
        } else {
            obtainedCards.put(key, Integer.valueOf(1));
            logger.info("Obtained " + key + " (" + rarity.name() + "). Creating new map entry.");
        }

        UnlockTracker.markCardAsSeen(key);
    }

    public static void clear() {
        logger.info("Clearing CardHelper (obtained cards)");
        removedCards.clear();
        obtainedCards.clear();
    }

    public static Color getColor(int r, int g, int b) {
        return new Color(r / 255.0F, g / 255.0F, b / 255.0F, 1.0F);
    }

    public static Color getColor(float r, float g, float b) {
        return new Color(r / 255.0F, g / 255.0F, b / 255.0F, 1.0F);
    }

    public static class CardInfo {
        String id;
        String name;
        AbstractCard.CardRarity rarity;
        AbstractCard.CardColor color;

        public CardInfo(String id, String name, AbstractCard.CardRarity rarity, AbstractCard.CardColor color) {
            this.id = id;
            this.name = name;
            this.rarity = rarity;
            this.color = color;
        }
    }

    public static boolean hasCardWithXDamage(int damage) {
        for (AbstractCard c : AbstractDungeon.player.masterDeck.group) {
            if (c.type == AbstractCard.CardType.ATTACK && c.baseDamage >= 10) {
                logger.info(c.name + " is 10 Attack!");
                return true;
            }
        }
        return false;
    }

    public static boolean hasCardWithID(String targetID) {
        for (AbstractCard c : AbstractDungeon.player.masterDeck.group) {
            if (c.cardID.equals(targetID)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasCardType(AbstractCard.CardType hasType) {
        for (AbstractCard c : AbstractDungeon.player.masterDeck.group) {
            if (c.type == hasType) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasCardWithType(AbstractCard.CardType type) {
        for (AbstractCard c : (CardGroup.getGroupWithoutBottledCards(AbstractDungeon.player.masterDeck)).group) {
            if (c.type == type) {
                return true;
            }
        }
        return false;
    }

    public static AbstractCard returnCardOfType(AbstractCard.CardType type, Random rng) {
        ArrayList<AbstractCard> cards = new ArrayList<>();

        for (AbstractCard c : (CardGroup.getGroupWithoutBottledCards(AbstractDungeon.player.masterDeck)).group) {
            if (c.type == type) {
                cards.add(c);
            }
        }

        return cards.remove(rng.random(cards.size() - 1));
    }

    public static boolean hasUpgradedCard() {
        for (AbstractCard c : (CardGroup.getGroupWithoutBottledCards(AbstractDungeon.player.masterDeck)).group) {
            if (c.upgraded == true) {
                return true;
            }
        }
        return false;
    }

    public static AbstractCard returnUpgradedCard(Random rng) {
        ArrayList<AbstractCard> cards = new ArrayList<>();

        for (AbstractCard c : (CardGroup.getGroupWithoutBottledCards(AbstractDungeon.player.masterDeck)).group) {
            if (c.upgraded == true) {
                cards.add(c);
            }
        }

        return cards.remove(rng.random(cards.size() - 1));
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\helpers\CardHelper
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


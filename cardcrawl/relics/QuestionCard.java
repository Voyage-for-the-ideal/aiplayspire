package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class QuestionCard extends AbstractRelic {
    private static final int CARDS_ADDED = 1;
    public static final String ID = "Question Card";

    public QuestionCard() {
        super("Question Card", "questionCard.png", RelicTier.UNCOMMON, LandingSound.FLAT);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public boolean canSpawn() {
        return (Settings.isEndless || AbstractDungeon.floorNum <= 48);
    }

    public int changeNumberOfCardsInReward(int numberOfCards) {
        return numberOfCards + 1;
    }

    public AbstractRelic makeCopy() {
        return new QuestionCard();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * QuestionCard.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


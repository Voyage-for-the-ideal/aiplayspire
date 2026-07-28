package com.megacrit.cardcrawl.blights;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.BlightStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class TimeMaze extends AbstractBlight {
    public static final String ID = "TimeMaze";
    private static final BlightStrings blightStrings = CardCrawlGame.languagePack.getBlightString("TimeMaze");
    public static final String NAME = blightStrings.NAME;
    public static final String[] DESC = blightStrings.DESCRIPTION;
    private static final int CARD_AMT = 15;

    public TimeMaze() {
        super("TimeMaze", NAME, DESC[0] + '\017' + DESC[1], "maze.png", true);
        this.counter = 15;
    }

    public void onPlayCard(AbstractCard card, AbstractMonster m) {
        if (this.counter < 15 && card.type != AbstractCard.CardType.CURSE) {
            this.counter++;
            if (this.counter >= 15) {
                flash();
            }
        }
    }

    public boolean canPlay(AbstractCard card) {
        if (this.counter >= 15 && card.type != AbstractCard.CardType.CURSE) {
            card.cantUseMessage = DESC[2] + '\017' + DESC[1];
            return false;
        }
        return true;
    }

    public void onVictory() {
        this.counter = -1;
    }

    public void atBattleStart() {
        this.counter = 0;
    }

    public void atTurnStart() {
        this.counter = 0;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\blights\TimeMaze.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




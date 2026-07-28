package com.megacrit.cardcrawl.cards.status;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Slimed extends AbstractCard {
    public static final String ID = "Slimed";
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Slimed");

    public Slimed() {
        super("Slimed", cardStrings.NAME, "status/slimed", 1, cardStrings.DESCRIPTION, CardType.STATUS,
                CardColor.COLORLESS, CardRarity.COMMON, CardTarget.SELF);

        this.exhaust = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
    }

    public void upgrade() {
    }

    public AbstractCard makeCopy() {
        return new Slimed();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\status\
 * Slimed.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


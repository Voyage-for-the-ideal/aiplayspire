package com.megacrit.cardcrawl.cards.curses;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class CurseOfTheBell extends AbstractCard {
    public static final String ID = "CurseOfTheBell";
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("CurseOfTheBell");

    public CurseOfTheBell() {
        super("CurseOfTheBell", cardStrings.NAME, "curse/curse_of_the_bell", -2, cardStrings.DESCRIPTION,
                CardType.CURSE, CardColor.CURSE, CardRarity.SPECIAL, CardTarget.NONE);
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
    }

    public void upgrade() {
    }

    public AbstractCard makeCopy() {
        return new CurseOfTheBell();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\curses\
 * CurseOfTheBell.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




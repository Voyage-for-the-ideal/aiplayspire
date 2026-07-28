package com.megacrit.cardcrawl.cards.curses;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ExhaustSpecificCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Clumsy extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Clumsy");
    public static final String ID = "Clumsy";

    public Clumsy() {
        super("Clumsy", cardStrings.NAME, "curse/clumsy", -2, cardStrings.DESCRIPTION, CardType.CURSE, CardColor.CURSE,
                CardRarity.CURSE, CardTarget.NONE);

        this.isEthereal = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
    }

    public void triggerOnEndOfPlayerTurn() {
        addToTop((AbstractGameAction) new ExhaustSpecificCardAction(this, AbstractDungeon.player.hand));
    }

    public void upgrade() {
    }

    public AbstractCard makeCopy() {
        return new Clumsy();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\curses\
 * Clumsy.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




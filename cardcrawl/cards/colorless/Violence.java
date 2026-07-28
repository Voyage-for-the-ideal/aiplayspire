package com.megacrit.cardcrawl.cards.colorless;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.utility.DrawPileToHandAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Violence extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Violence");
    public static final String ID = "Violence";

    public Violence() {
        super("Violence", cardStrings.NAME, "colorless/skill/violence", 0, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.COLORLESS, CardRarity.RARE, CardTarget.NONE);

        this.baseMagicNumber = 3;
        this.magicNumber = this.baseMagicNumber;
        this.exhaust = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new DrawPileToHandAction(this.magicNumber, CardType.ATTACK));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeMagicNumber(1);
        }
    }

    public AbstractCard makeCopy() {
        return new Violence();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\colorless\
 * Violence.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




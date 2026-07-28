package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class DEPRECATEDContemplate extends AbstractCard {
    public static final String ID = "Contemplate";
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Contemplate");

    public DEPRECATEDContemplate() {
        super("Contemplate", cardStrings.NAME, null, 0, cardStrings.DESCRIPTION, CardType.SKILL, CardColor.PURPLE,
                CardRarity.COMMON, CardTarget.SELF);
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
            initializeDescription();
        }
    }

    public AbstractCard makeCopy() {
        return new DEPRECATEDContemplate();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\deprecated\
 * DEPRECATEDContemplate.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




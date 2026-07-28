package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class DEPRECATEDSmile extends AbstractCard {
    public static final String ID = "Smile";
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Smile");

    public DEPRECATEDSmile() {
        super("Smile", cardStrings.NAME, null, 1, cardStrings.DESCRIPTION, CardType.SKILL, CardColor.PURPLE,
                CardRarity.COMMON, CardTarget.SELF);

        this.exhaust = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            updateCost(0);
        }
    }

    public AbstractCard makeCopy() {
        return new DEPRECATEDSmile();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\deprecated\
 * DEPRECATEDSmile.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




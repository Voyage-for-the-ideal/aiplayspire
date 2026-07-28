package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.deprecated.DEPRECATEDExperiencedAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class DEPRECATEDExperienced extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Experienced");
    public static final String ID = "Experienced";

    public DEPRECATEDExperienced() {
        super("Experienced", cardStrings.NAME, null, 1, cardStrings.DESCRIPTION, CardType.SKILL, CardColor.PURPLE,
                CardRarity.UNCOMMON, CardTarget.SELF);

        this.baseBlock = 5;
        this.block = 5;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new DEPRECATEDExperiencedAction(this.block, this));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeBlock(2);
        }
    }

    public AbstractCard makeCopy() {
        return new DEPRECATEDExperienced();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\deprecated\
 * DEPRECATEDExperienced.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




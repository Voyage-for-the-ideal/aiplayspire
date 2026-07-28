package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInDrawPileAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.tempCards.Miracle;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class DEPRECATEDBlessed extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Blessed");
    public static final String ID = "Blessed";

    public DEPRECATEDBlessed() {
        super("Blessed", cardStrings.NAME, null, 0, cardStrings.DESCRIPTION, CardType.SKILL, CardColor.PURPLE,
                CardRarity.UNCOMMON, CardTarget.SELF);

        this.exhaust = true;
        this.baseMagicNumber = 2;
        this.magicNumber = this.baseMagicNumber;
        this.cardsToPreview = (AbstractCard) new Miracle();
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        AbstractCard miracle = CardLibrary.getCard("Miracle").makeCopy();

        if (this.upgraded) {
            miracle.upgrade();
        }

        addToBot((AbstractGameAction) new MakeTempCardInDrawPileAction(miracle, this.magicNumber, true, true, false));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            this.cardsToPreview.upgrade();
            this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
            initializeDescription();
        }
    }

    public AbstractCard makeCopy() {
        return new DEPRECATEDBlessed();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\deprecated\
 * DEPRECATEDBlessed.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




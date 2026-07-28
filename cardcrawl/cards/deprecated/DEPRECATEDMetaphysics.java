package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInDrawPileAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class DEPRECATEDMetaphysics extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Metaphysics");
    public static final String ID = "Metaphysics";

    public DEPRECATEDMetaphysics() {
        super("Metaphysics", cardStrings.NAME, "purple/skill/metaphysics", 1, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.PURPLE, CardRarity.RARE, CardTarget.SELF);

        this.cardsToPreview = new DEPRECATEDCausality();
        this.exhaust = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new MakeTempCardInDrawPileAction(this.cardsToPreview.makeStatEquivalentCopy(), 1,
                true, true));
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
        return new DEPRECATEDMetaphysics();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\deprecated\
 * DEPRECATEDMetaphysics.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




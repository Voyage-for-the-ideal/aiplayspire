package com.megacrit.cardcrawl.cards.green;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DiscardAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Acrobatics extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Acrobatics");
    public static final String ID = "Acrobatics";

    public Acrobatics() {
        super("Acrobatics", cardStrings.NAME, "green/skill/acrobatics", 1, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.GREEN, CardRarity.COMMON, CardTarget.NONE);

        this.baseMagicNumber = 3;
        this.magicNumber = this.baseMagicNumber;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new DrawCardAction((AbstractCreature) p, this.magicNumber));
        addToBot((AbstractGameAction) new DiscardAction((AbstractCreature) p, (AbstractCreature) p, 1, false));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeMagicNumber(1);
        }
    }

    public AbstractCard makeCopy() {
        return new Acrobatics();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\green\
 * Acrobatics.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




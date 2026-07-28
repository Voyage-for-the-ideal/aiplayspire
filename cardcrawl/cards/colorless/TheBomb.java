package com.megacrit.cardcrawl.cards.colorless;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.TheBombPower;

public class TheBomb extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("The Bomb");
    public static final String ID = "The Bomb";

    public TheBomb() {
        super("The Bomb", cardStrings.NAME, "colorless/skill/the_bomb", 2, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.COLORLESS, CardRarity.RARE, CardTarget.SELF);

        this.baseMagicNumber = 40;
        this.magicNumber = this.baseMagicNumber;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) p, (AbstractCreature) p,
                (AbstractPower) new TheBombPower((AbstractCreature) p, 3, this.magicNumber), 3));
    }

    public AbstractCard makeCopy() {
        return new TheBomb();
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeMagicNumber(10);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\colorless\
 * TheBomb.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




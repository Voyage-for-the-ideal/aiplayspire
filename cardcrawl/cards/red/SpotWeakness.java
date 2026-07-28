package com.megacrit.cardcrawl.cards.red;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.unique.SpotWeaknessAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class SpotWeakness extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Spot Weakness");
    public static final String ID = "Spot Weakness";

    public SpotWeakness() {
        super("Spot Weakness", cardStrings.NAME, "red/skill/spot_weakness", 1, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.RED, CardRarity.UNCOMMON, CardTarget.SELF_AND_ENEMY);

        this.baseMagicNumber = 3;
        this.magicNumber = this.baseMagicNumber;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new SpotWeaknessAction(this.magicNumber, m));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeMagicNumber(1);
        }
    }

    public AbstractCard makeCopy() {
        return new SpotWeakness();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\red\
 * SpotWeakness.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


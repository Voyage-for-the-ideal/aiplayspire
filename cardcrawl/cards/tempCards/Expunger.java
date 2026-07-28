package com.megacrit.cardcrawl.cards.tempCards;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.watcher.ExpungeVFXAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

import java.util.ArrayList;

public class Expunger
        extends AbstractCard {
    public static final String ID = "Expunger";
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Expunger");

    public Expunger() {
        super("Expunger", cardStrings.NAME, "colorless/attack/expunger", 1, cardStrings.DESCRIPTION, CardType.ATTACK,
                CardColor.COLORLESS, CardRarity.SPECIAL, CardTarget.ENEMY);

        this.baseDamage = 9;
    }

    public void setX(int amount) {
        this.magicNumber = amount;

        if (this.upgraded) {
            this.magicNumber++;
        }

        this.baseMagicNumber = this.magicNumber;

        this.rawDescription = (this.baseMagicNumber == 1) ? cardStrings.EXTENDED_DESCRIPTION[1]
                : cardStrings.EXTENDED_DESCRIPTION[0];

        initializeDescription();
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        for (int i = 0; i < this.magicNumber; i++) {
            addToBot((AbstractGameAction) new ExpungeVFXAction(m));
            addToBot((AbstractGameAction) new DamageAction((AbstractCreature) m,
                    new DamageInfo((AbstractCreature) p, this.damage, this.damageTypeForTurn),
                    AbstractGameAction.AttackEffect.NONE));
        }
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(6);
        }
    }

    public AbstractCard makeCopy() {
        return new Expunger();
    }

    public AbstractCard makeStatEquivalentCopy() {
        AbstractCard card = super.makeStatEquivalentCopy();
        card.baseMagicNumber = this.baseMagicNumber;
        card.magicNumber = this.magicNumber;
        card.description = (ArrayList) this.description.clone();
        return card;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\tempCards\
 * Expunger.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


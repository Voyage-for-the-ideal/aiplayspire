package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class DEPRECATEDIntrospection extends AbstractCard {
    public static final String ID = "Introspection";
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Introspection");

    public DEPRECATEDIntrospection() {
        super("Introspection", cardStrings.NAME, null, 1, cardStrings.DESCRIPTION, CardType.SKILL, CardColor.PURPLE,
                CardRarity.COMMON, CardTarget.SELF);

        this.exhaust = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new RemoveSpecificPowerAction((AbstractCreature) p, (AbstractCreature) p,
                "Frail"));
        addToBot((AbstractGameAction) new RemoveSpecificPowerAction((AbstractCreature) p, (AbstractCreature) p,
                "Vulnerable"));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            this.exhaust = false;
            this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
            initializeDescription();
        }
    }

    public AbstractCard makeCopy() {
        return new DEPRECATEDIntrospection();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\deprecated\
 * DEPRECATEDIntrospection.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




package com.megacrit.cardcrawl.cards.colorless;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.unique.RitualDaggerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class RitualDagger extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("RitualDagger");
    public static final String ID = "RitualDagger";

    public RitualDagger() {
        super("RitualDagger", cardStrings.NAME, "colorless/attack/ritual_dagger", 1, cardStrings.DESCRIPTION,
                CardType.ATTACK, CardColor.COLORLESS, CardRarity.SPECIAL, CardTarget.ENEMY);

        this.misc = 15;
        this.baseMagicNumber = 3;
        this.magicNumber = this.baseMagicNumber;
        this.baseDamage = this.misc;
        this.exhaust = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new RitualDaggerAction((AbstractCreature) m,
                new DamageInfo((AbstractCreature) p, this.damage, this.damageTypeForTurn), this.magicNumber,
                this.uuid));
    }

    public void applyPowers() {
        this.baseBlock = this.misc;
        super.applyPowers();
        initializeDescription();
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeMagicNumber(2);
            upgradeName();
        }
    }

    public AbstractCard makeCopy() {
        return new RitualDagger();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\colorless\
 * RitualDagger.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




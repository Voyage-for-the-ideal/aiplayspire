package com.megacrit.cardcrawl.cards.purple;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.AttackDamageRandomEnemyAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Ragnarok extends AbstractCard {
    public static final String ID = "Ragnarok";
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Ragnarok");

    public Ragnarok() {
        super("Ragnarok", cardStrings.NAME, "purple/attack/ragnarok", 3, cardStrings.DESCRIPTION, CardType.ATTACK,
                CardColor.PURPLE, CardRarity.RARE, CardTarget.ALL_ENEMY);

        this.baseDamage = 5;
        this.baseMagicNumber = 5;
        this.magicNumber = this.baseMagicNumber;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        for (int i = 0; i < this.magicNumber; i++) {
            addToBot((AbstractGameAction) new AttackDamageRandomEnemyAction(this,
                    AbstractGameAction.AttackEffect.LIGHTNING));
        }
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(1);
            upgradeMagicNumber(1);
        }
    }

    public AbstractCard makeCopy() {
        return new Ragnarok();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\purple\
 * Ragnarok.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


package com.megacrit.cardcrawl.cards.red;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.unique.WhirlwindAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Whirlwind extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Whirlwind");
    public static final String ID = "Whirlwind";

    public Whirlwind() {
        super("Whirlwind", cardStrings.NAME, "red/attack/whirlwind", -1, cardStrings.DESCRIPTION, CardType.ATTACK,
                CardColor.RED, CardRarity.UNCOMMON, CardTarget.ALL_ENEMY);

        this.baseDamage = 5;
        this.isMultiDamage = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new WhirlwindAction(p, this.multiDamage, this.damageTypeForTurn,
                this.freeToPlayOnce, this.energyOnUse));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(3);
        }
    }

    public AbstractCard makeCopy() {
        return new Whirlwind();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\red\
 * Whirlwind.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


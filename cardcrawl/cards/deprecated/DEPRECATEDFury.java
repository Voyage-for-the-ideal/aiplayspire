package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.watcher.ChangeStanceAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class DEPRECATEDFury extends AbstractCard {
    public static final String ID = "Fury";
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Fury");

    public DEPRECATEDFury() {
        super("Fury", cardStrings.NAME, null, 2, cardStrings.DESCRIPTION, CardType.ATTACK, CardColor.PURPLE,
                CardRarity.BASIC, CardTarget.ENEMY);

        this.baseDamage = 8;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new DamageAction((AbstractCreature) m,
                new DamageInfo((AbstractCreature) p, this.damage, this.damageTypeForTurn),
                AbstractGameAction.AttackEffect.BLUNT_LIGHT));
        addToBot((AbstractGameAction) new ChangeStanceAction("Wrath"));
    }

    public AbstractCard makeCopy() {
        return new DEPRECATEDFury();
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(4);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\deprecated\
 * DEPRECATEDFury.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




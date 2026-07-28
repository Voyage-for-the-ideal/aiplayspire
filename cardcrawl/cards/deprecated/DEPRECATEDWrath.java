package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.watcher.ChangeStanceAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class DEPRECATEDWrath extends AbstractCard {
    public static final String ID = "Wrath";
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Wrath");

    public DEPRECATEDWrath() {
        super("Wrath", cardStrings.NAME, null, 1, cardStrings.DESCRIPTION, CardType.SKILL, CardColor.PURPLE,
                CardRarity.UNCOMMON, CardTarget.NONE);
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new ChangeStanceAction("Wrath"));
    }

    public AbstractCard makeCopy() {
        return new DEPRECATEDWrath();
    }

    public void upgrade() {
        upgradeName();
        upgradeBaseCost(0);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\deprecated\
 * DEPRECATEDWrath.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




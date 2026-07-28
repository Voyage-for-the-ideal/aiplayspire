package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.unique.MulticastAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class MultiCast extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Multi-Cast");
    public static final String ID = "Multi-Cast";

    public MultiCast() {
        super("Multi-Cast", cardStrings.NAME, "blue/skill/multicast", -1, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.BLUE, CardRarity.RARE, CardTarget.NONE);

        this.showEvokeValue = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new MulticastAction(p, this.energyOnUse, this.upgraded, this.freeToPlayOnce));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
            initializeDescription();
        }
    }

    public AbstractCard makeCopy() {
        return new MultiCast();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\blue\
 * MultiCast.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




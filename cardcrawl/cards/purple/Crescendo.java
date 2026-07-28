package com.megacrit.cardcrawl.cards.purple;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.watcher.ChangeStanceAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Crescendo extends AbstractCard {
    public static final String ID = "Crescendo";
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Crescendo");

    public Crescendo() {
        super("Crescendo", cardStrings.NAME, "purple/skill/crescendo", 1, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.PURPLE, CardRarity.COMMON, CardTarget.SELF);

        this.exhaust = true;
        this.selfRetain = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new ChangeStanceAction("Wrath"));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeBaseCost(0);
        }
    }

    public AbstractCard makeCopy() {
        return new Crescendo();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\purple\
 * Crescendo.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


package com.megacrit.cardcrawl.cards.red;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ExhaustAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class TrueGrit extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("True Grit");
    public static final String ID = "True Grit";

    public TrueGrit() {
        super("True Grit", cardStrings.NAME, "red/skill/true_grit", 1, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.RED, CardRarity.COMMON, CardTarget.SELF);

        this.baseBlock = 7;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new GainBlockAction((AbstractCreature) p, (AbstractCreature) p, this.block));
        if (this.upgraded) {
            addToBot((AbstractGameAction) new ExhaustAction(1, false));
        } else {
            addToBot((AbstractGameAction) new ExhaustAction(1, true, false, false));
        }
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeBlock(2);
            this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
            initializeDescription();
        }
    }

    public AbstractCard makeCopy() {
        return new TrueGrit();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\red\TrueGrit
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


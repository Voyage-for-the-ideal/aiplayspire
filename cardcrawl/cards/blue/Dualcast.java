package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.defect.AnimateOrbAction;
import com.megacrit.cardcrawl.actions.defect.EvokeOrbAction;
import com.megacrit.cardcrawl.actions.defect.EvokeWithoutRemovingOrbAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Dualcast extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Dualcast");
    public static final String ID = "Dualcast";

    public Dualcast() {
        super("Dualcast", cardStrings.NAME, "blue/skill/dualcast", 1, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.BLUE, CardRarity.BASIC, CardTarget.NONE);

        this.showEvokeValue = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new AnimateOrbAction(1));
        addToBot((AbstractGameAction) new EvokeWithoutRemovingOrbAction(1));
        addToBot((AbstractGameAction) new AnimateOrbAction(1));
        addToBot((AbstractGameAction) new EvokeOrbAction(1));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeBaseCost(0);
        }
    }

    public AbstractCard makeCopy() {
        return new Dualcast();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\blue\
 * Dualcast.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




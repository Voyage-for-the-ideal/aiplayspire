package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class DEPRECATEDBrillianceAura extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("BrillianceAura");
    public static final String ID = "BrillianceAura";

    public DEPRECATEDBrillianceAura() {
        super("BrillianceAura", cardStrings.NAME, null, 2, cardStrings.DESCRIPTION, CardType.SKILL, CardColor.PURPLE,
                CardRarity.RARE, CardTarget.NONE);

        this.selfRetain = true;
        this.exhaust = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
    }

    public void atTurnStartPreDraw() {
        addToBot((AbstractGameAction) new DrawCardAction(1));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeBaseCost(1);
        }
    }

    public AbstractCard makeCopy() {
        return new DEPRECATEDBrillianceAura();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\deprecated\
 * DEPRECATEDBrillianceAura.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




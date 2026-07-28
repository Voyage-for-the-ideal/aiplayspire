package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.watcher.PerfectedFormAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class DEPRECATEDPerfectedForm extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("PerfectedForm");
    public static final String ID = "PerfectedForm";

    public DEPRECATEDPerfectedForm() {
        super("PerfectedForm", cardStrings.NAME, null, 0, cardStrings.DESCRIPTION, CardType.SKILL, CardColor.PURPLE,
                CardRarity.RARE, CardTarget.SELF);

        this.exhaust = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new PerfectedFormAction());
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            initializeDescription();
            this.exhaust = false;
        }
    }

    public AbstractCard makeCopy() {
        return new DEPRECATEDPerfectedForm();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\deprecated\
 * DEPRECATEDPerfectedForm.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




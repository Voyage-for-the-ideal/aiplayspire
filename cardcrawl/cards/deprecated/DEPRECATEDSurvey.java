package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.watcher.VigorPower;

public class DEPRECATEDSurvey extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Survey");
    public static final String ID = "Survey";

    public DEPRECATEDSurvey() {
        super("Survey", cardStrings.NAME, null, 1, cardStrings.DESCRIPTION, CardType.SKILL, CardColor.PURPLE,
                CardRarity.COMMON, CardTarget.SELF);

        this.baseBlock = 8;
        this.baseMagicNumber = 2;
        this.magicNumber = this.baseMagicNumber;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new GainBlockAction((AbstractCreature) p, (AbstractCreature) p, this.block));

        addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) p, (AbstractCreature) p,
                (AbstractPower) new VigorPower((AbstractCreature) p, this.magicNumber), this.magicNumber));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeBlock(3);
            upgradeMagicNumber(1);
        }
    }

    public AbstractCard makeCopy() {
        return new DEPRECATEDSurvey();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\deprecated\
 * DEPRECATEDSurvey.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




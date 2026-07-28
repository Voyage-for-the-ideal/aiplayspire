package com.megacrit.cardcrawl.cards.red;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInHandAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.status.Wound;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class PowerThrough extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Power Through");
    public static final String ID = "Power Through";

    public PowerThrough() {
        super("Power Through", cardStrings.NAME, "red/skill/power_through", 1, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.RED, CardRarity.UNCOMMON, CardTarget.SELF);

        this.baseBlock = 15;
        this.cardsToPreview = (AbstractCard) new Wound();
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new MakeTempCardInHandAction((AbstractCard) new Wound(), 2));
        addToBot((AbstractGameAction) new GainBlockAction((AbstractCreature) p, (AbstractCreature) p, this.block));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeBlock(5);
        }
    }

    public AbstractCard makeCopy() {
        return new PowerThrough();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\red\
 * PowerThrough.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


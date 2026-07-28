package com.megacrit.cardcrawl.cards.red;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class ShrugItOff extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Shrug It Off");
    public static final String ID = "Shrug It Off";

    public ShrugItOff() {
        super("Shrug It Off", cardStrings.NAME, "red/skill/shrug_it_off", 1, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.RED, CardRarity.COMMON, CardTarget.SELF);

        this.baseBlock = 8;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new GainBlockAction((AbstractCreature) p, (AbstractCreature) p, this.block));
        addToBot((AbstractGameAction) new DrawCardAction((AbstractCreature) p, 1));
    }

    public AbstractCard makeCopy() {
        return new ShrugItOff();
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeBlock(3);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\red\
 * ShrugItOff.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


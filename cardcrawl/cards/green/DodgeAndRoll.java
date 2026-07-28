package com.megacrit.cardcrawl.cards.green;

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
import com.megacrit.cardcrawl.powers.NextTurnBlockPower;

public class DodgeAndRoll extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Dodge and Roll");
    public static final String ID = "Dodge and Roll";

    public DodgeAndRoll() {
        super("Dodge and Roll", cardStrings.NAME, "green/skill/dodge_and_roll", 1, cardStrings.DESCRIPTION,
                CardType.SKILL, CardColor.GREEN, CardRarity.COMMON, CardTarget.SELF);

        this.baseBlock = 4;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new GainBlockAction((AbstractCreature) p, (AbstractCreature) p, this.block));
        addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) p, (AbstractCreature) p,
                (AbstractPower) new NextTurnBlockPower((AbstractCreature) p, this.block), this.block));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeBlock(2);
        }
    }

    public AbstractCard makeCopy() {
        return new DodgeAndRoll();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\green\
 * DodgeAndRoll.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


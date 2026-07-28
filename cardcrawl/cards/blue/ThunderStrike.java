package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.defect.NewThunderStrikeAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.orbs.AbstractOrb;

public class ThunderStrike extends AbstractCard {
    public static final String ID = "Thunder Strike";
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Thunder Strike");

    public ThunderStrike() {
        super("Thunder Strike", cardStrings.NAME, "blue/attack/thunder_strike", 3, cardStrings.DESCRIPTION,
                CardType.ATTACK, CardColor.BLUE, CardRarity.RARE, CardTarget.ALL_ENEMY);

        this.baseMagicNumber = 0;
        this.magicNumber = 0;
        this.baseDamage = 7;
        this.tags.add(CardTags.STRIKE);
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        this.baseMagicNumber = 0;
        for (AbstractOrb o : AbstractDungeon.actionManager.orbsChanneledThisCombat) {
            if (o instanceof com.megacrit.cardcrawl.orbs.Lightning) {
                this.baseMagicNumber++;
            }
        }
        this.magicNumber = this.baseMagicNumber;

        for (int i = 0; i < this.magicNumber; i++) {
            addToBot((AbstractGameAction) new NewThunderStrikeAction(this));
        }
    }

    public void applyPowers() {
        super.applyPowers();

        this.baseMagicNumber = 0;
        this.magicNumber = 0;
        for (AbstractOrb o : AbstractDungeon.actionManager.orbsChanneledThisCombat) {
            if (o instanceof com.megacrit.cardcrawl.orbs.Lightning) {
                this.baseMagicNumber++;
            }
        }

        if (this.baseMagicNumber > 0) {
            this.rawDescription = cardStrings.DESCRIPTION + cardStrings.EXTENDED_DESCRIPTION[0];
            initializeDescription();
        }
    }

    public void onMoveToDiscard() {
        this.rawDescription = cardStrings.DESCRIPTION;
        initializeDescription();
    }

    public void calculateCardDamage(AbstractMonster mo) {
        super.calculateCardDamage(mo);
        if (this.baseMagicNumber > 0) {
            this.rawDescription = cardStrings.DESCRIPTION + cardStrings.EXTENDED_DESCRIPTION[0];
        }
        initializeDescription();
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(2);
        }
    }

    public AbstractCard makeCopy() {
        return new ThunderStrike();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\blue\
 * ThunderStrike.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




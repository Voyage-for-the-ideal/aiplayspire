package com.megacrit.cardcrawl.cards.colorless;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.VulnerablePower;

public class Trip extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Trip");
    public static final String ID = "Trip";

    public Trip() {
        super("Trip", cardStrings.NAME, "colorless/skill/trip", 0, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.COLORLESS, CardRarity.UNCOMMON, CardTarget.ENEMY);

        this.baseMagicNumber = 2;
        this.magicNumber = this.baseMagicNumber;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        if (!this.upgraded) {
            addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) m, (AbstractCreature) p,
                    (AbstractPower) new VulnerablePower((AbstractCreature) m, this.magicNumber, false),
                    this.magicNumber));
        } else {
            for (AbstractMonster mo : (AbstractDungeon.getCurrRoom()).monsters.monsters) {
                addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) mo, (AbstractCreature) p,
                        (AbstractPower) new VulnerablePower((AbstractCreature) mo, this.magicNumber, false),
                        this.magicNumber, true, AbstractGameAction.AttackEffect.NONE));
            }
        }
    }

    public AbstractCard makeCopy() {
        return new Trip();
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            this.target = CardTarget.ALL_ENEMY;
            this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
            initializeDescription();
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\colorless\
 * Trip.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.defect.ChannelAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.orbs.Lightning;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.ElectroPower;

public class Electrodynamics extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Electrodynamics");
    public static final String ID = "Electrodynamics";

    public Electrodynamics() {
        super("Electrodynamics", cardStrings.NAME, "blue/power/electrodynamics", 2, cardStrings.DESCRIPTION,
                CardType.POWER, CardColor.BLUE, CardRarity.RARE, CardTarget.SELF);

        this.baseMagicNumber = 2;
        this.magicNumber = this.baseMagicNumber;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        if (!p.hasPower("Electrodynamics")) {
            addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) p, (AbstractCreature) p,
                    (AbstractPower) new ElectroPower((AbstractCreature) p)));
        }
        for (int i = 0; i < this.magicNumber; i++) {
            Lightning lightning = new Lightning();
            addToBot((AbstractGameAction) new ChannelAction((AbstractOrb) lightning));
        }
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeMagicNumber(1);
        }
    }

    public AbstractCard makeCopy() {
        return new Electrodynamics();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\blue\
 * Electrodynamics.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




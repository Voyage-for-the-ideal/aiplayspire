package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.VulnerablePower;

public class BeamCell extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Beam Cell");
    public static final String ID = "Beam Cell";

    public BeamCell() {
        super("Beam Cell", cardStrings.NAME, "blue/attack/beam_cell", 0, cardStrings.DESCRIPTION, CardType.ATTACK,
                CardColor.BLUE, CardRarity.COMMON, CardTarget.ENEMY);

        this.baseDamage = 3;
        this.baseMagicNumber = 1;
        this.magicNumber = this.baseMagicNumber;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new DamageAction((AbstractCreature) m,
                new DamageInfo((AbstractCreature) p, this.damage, this.damageTypeForTurn),
                AbstractGameAction.AttackEffect.BLUNT_HEAVY));
        addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) m, (AbstractCreature) p,
                (AbstractPower) new VulnerablePower((AbstractCreature) m, this.magicNumber, false), this.magicNumber,
                true, AbstractGameAction.AttackEffect.NONE));
    }

    public AbstractCard makeCopy() {
        return new BeamCell();
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeDamage(1);
            upgradeMagicNumber(1);
            upgradeName();
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\blue\
 * BeamCell.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




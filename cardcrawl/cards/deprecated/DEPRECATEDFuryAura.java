package com.megacrit.cardcrawl.cards.deprecated;

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
import com.megacrit.cardcrawl.powers.LoseStrengthPower;
import com.megacrit.cardcrawl.powers.StrengthPower;

public class DEPRECATEDFuryAura extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("FuryAura");
    public static final String ID = "FuryAura";

    public DEPRECATEDFuryAura() {
        super("FuryAura", cardStrings.NAME, null, 2, cardStrings.DESCRIPTION, CardType.SKILL, CardColor.PURPLE,
                CardRarity.UNCOMMON, CardTarget.SELF);

        this.selfRetain = true;
        this.magicNumber = 4;
        this.baseMagicNumber = 4;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) p, (AbstractCreature) p,
                (AbstractPower) new StrengthPower((AbstractCreature) p, this.magicNumber), this.magicNumber));
        addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) p, (AbstractCreature) p,
                (AbstractPower) new LoseStrengthPower((AbstractCreature) p, this.magicNumber), this.magicNumber));
    }

    public void atTurnStartPreDraw() {
        AbstractPlayer abstractPlayer = AbstractDungeon.player;
        addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) abstractPlayer,
                (AbstractCreature) abstractPlayer,
                (AbstractPower) new StrengthPower((AbstractCreature) abstractPlayer, 1), 1));
        addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) abstractPlayer,
                (AbstractCreature) abstractPlayer,
                (AbstractPower) new LoseStrengthPower((AbstractCreature) abstractPlayer, 1), 1));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeMagicNumber(2);
        }
    }

    public AbstractCard makeCopy() {
        return new DEPRECATEDFuryAura();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\deprecated\
 * DEPRECATEDFuryAura.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




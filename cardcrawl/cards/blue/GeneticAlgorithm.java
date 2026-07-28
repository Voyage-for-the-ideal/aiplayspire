package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.defect.IncreaseMiscAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class GeneticAlgorithm extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Genetic Algorithm");
    public static final String ID = "Genetic Algorithm";

    public GeneticAlgorithm() {
        super("Genetic Algorithm", cardStrings.NAME, "blue/skill/genetic_algorithm", 1, cardStrings.DESCRIPTION,
                CardType.SKILL, CardColor.BLUE, CardRarity.UNCOMMON, CardTarget.SELF);

        this.misc = 1;
        this.baseMagicNumber = 2;
        this.magicNumber = this.baseMagicNumber;
        this.baseBlock = this.misc;
        this.exhaust = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new IncreaseMiscAction(this.uuid, this.misc, this.magicNumber));
        addToBot((AbstractGameAction) new GainBlockAction((AbstractCreature) p, (AbstractCreature) p, this.block));
    }

    public void applyPowers() {
        this.baseBlock = this.misc;
        super.applyPowers();
        initializeDescription();
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeMagicNumber(1);
            upgradeName();
        }
    }

    public AbstractCard makeCopy() {
        return new GeneticAlgorithm();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\blue\
 * GeneticAlgorithm.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




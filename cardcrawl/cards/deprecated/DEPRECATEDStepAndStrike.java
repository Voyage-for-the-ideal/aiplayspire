package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class DEPRECATEDStepAndStrike extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("StepAndStrike");
    public static final String ID = "StepAndStrike";

    public DEPRECATEDStepAndStrike() {
        super("StepAndStrike", cardStrings.NAME, "purple/attack/step_and_strike", 3, cardStrings.DESCRIPTION,
                CardType.ATTACK, CardColor.PURPLE, CardRarity.UNCOMMON, CardTarget.SELF_AND_ENEMY);

        this.baseDamage = 8;
        this.baseBlock = 8;
        this.tags.add(CardTags.STRIKE);
    }

    public void switchedStance() {
        setCostForTurn(this.costForTurn - 1);
    }

    public void triggerWhenDrawn() {
        super.triggerWhenDrawn();
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new GainBlockAction((AbstractCreature) p, (AbstractCreature) p, this.block));
        addToBot((AbstractGameAction) new DamageAction((AbstractCreature) m,
                new DamageInfo((AbstractCreature) p, this.damage, this.damageTypeForTurn),
                AbstractGameAction.AttackEffect.SLASH_HEAVY));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(2);
            upgradeBlock(2);
        }
    }

    public AbstractCard makeCopy() {
        return new DEPRECATEDStepAndStrike();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\deprecated\
 * DEPRECATEDStepAndStrike.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




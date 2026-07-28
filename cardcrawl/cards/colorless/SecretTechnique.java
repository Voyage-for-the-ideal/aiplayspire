package com.megacrit.cardcrawl.cards.colorless;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.unique.SkillFromDeckToHandAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class SecretTechnique extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Secret Technique");
    public static final String ID = "Secret Technique";

    public SecretTechnique() {
        super("Secret Technique", cardStrings.NAME, "colorless/skill/secret_technique", 0, cardStrings.DESCRIPTION,
                CardType.SKILL, CardColor.COLORLESS, CardRarity.RARE, CardTarget.NONE);

        this.exhaust = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new SkillFromDeckToHandAction(1));
    }

    public boolean canUse(AbstractPlayer p, AbstractMonster m) {
        boolean canUse = super.canUse(p, m);
        if (!canUse) {
            return false;
        }

        boolean hasSkill = false;
        for (AbstractCard c : p.drawPile.group) {
            if (c.type == CardType.SKILL) {
                hasSkill = true;
            }
        }

        if (!hasSkill) {
            this.cantUseMessage = cardStrings.EXTENDED_DESCRIPTION[0];
            canUse = false;
        }

        return canUse;
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            this.exhaust = false;
            this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
            initializeDescription();
        }
    }

    public AbstractCard makeCopy() {
        return new SecretTechnique();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\colorless\
 * SecretTechnique.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




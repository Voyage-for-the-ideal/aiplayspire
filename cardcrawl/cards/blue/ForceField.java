package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class ForceField extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Force Field");
    public static final String ID = "Force Field";

    public ForceField() {
        super("Force Field", cardStrings.NAME, "blue/skill/forcefield", 4, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.BLUE, CardRarity.UNCOMMON, CardTarget.SELF);

        this.baseBlock = 12;

        if (CardCrawlGame.dungeon != null && AbstractDungeon.currMapNode != null) {
            configureCostsOnNewCard();
        }
    }

    public void configureCostsOnNewCard() {
        for (AbstractCard c : AbstractDungeon.actionManager.cardsPlayedThisCombat) {
            if (c.type == CardType.POWER) {
                updateCost(-1);
            }
        }
    }

    public void triggerOnCardPlayed(AbstractCard c) {
        if (c.type == CardType.POWER) {
            updateCost(-1);
        }
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new GainBlockAction((AbstractCreature) p, (AbstractCreature) p, this.block));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeBlock(4);
        }
    }

    public AbstractCard makeCopy() {
        return new ForceField();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\blue\
 * ForceField.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.PlayTopCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class DEPRECATEDLetFateDecide extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("LetFateDecide");
    public static final String ID = "LetFateDecide";

    public DEPRECATEDLetFateDecide() {
        super("LetFateDecide", cardStrings.NAME, null, -1, cardStrings.DESCRIPTION, CardType.SKILL, CardColor.PURPLE,
                CardRarity.UNCOMMON, CardTarget.NONE);
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        for (int i = 0; i < this.energyOnUse; i++) {
            addToBot((AbstractGameAction) new PlayTopCardAction(

                    (AbstractCreature) (AbstractDungeon.getCurrRoom()).monsters.getRandomMonster(null, true,
                            AbstractDungeon.cardRandomRng),
                    false));
        }

        if (this.energyOnUse >= 3)
            ;
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
        }
    }

    public AbstractCard makeCopy() {
        return new DEPRECATEDLetFateDecide();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\deprecated\
 * DEPRECATEDLetFateDecide.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




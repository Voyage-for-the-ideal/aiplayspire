package com.megacrit.cardcrawl.cards.red;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.PlayTopCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Havoc extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Havoc");
    public static final String ID = "Havoc";

    public Havoc() {
        super("Havoc", cardStrings.NAME, "red/skill/havoc", 1, cardStrings.DESCRIPTION, CardType.SKILL, CardColor.RED,
                CardRarity.COMMON, CardTarget.NONE);
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new PlayTopCardAction(

                (AbstractCreature) (AbstractDungeon.getCurrRoom()).monsters.getRandomMonster(null, true,
                        AbstractDungeon.cardRandomRng),
                true));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeBaseCost(0);
        }
    }

    public AbstractCard makeCopy() {
        return new Havoc();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\red\Havoc.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


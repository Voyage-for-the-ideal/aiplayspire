package com.megacrit.cardcrawl.cards.purple;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.watcher.ChooseOneAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.optionCards.BecomeAlmighty;
import com.megacrit.cardcrawl.cards.optionCards.FameAndFortune;
import com.megacrit.cardcrawl.cards.optionCards.LiveForever;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

import java.util.ArrayList;

public class Wish extends AbstractCard {
    public static final String ID = "Wish";
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Wish");

    public Wish() {
        super("Wish", cardStrings.NAME, "purple/skill/wish", 3, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.PURPLE, CardRarity.RARE, CardTarget.NONE);

        this.baseDamage = 3;
        this.baseMagicNumber = 25;
        this.magicNumber = 25;
        this.baseBlock = 6;
        this.exhaust = true;

        this.tags.add(CardTags.HEALING);
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        ArrayList<AbstractCard> stanceChoices = new ArrayList<>();
        stanceChoices.add(new BecomeAlmighty());
        stanceChoices.add(new FameAndFortune());
        stanceChoices.add(new LiveForever());

        if (this.upgraded) {
            for (AbstractCard c : stanceChoices) {
                c.upgrade();
            }
        }
        addToBot((AbstractGameAction) new ChooseOneAction(stanceChoices));
    }

    public void applyPowers() {
    }

    public void calculateCardDamage(AbstractMonster mo) {
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(1);
            upgradeMagicNumber(5);
            upgradeBlock(2);
        }
    }

    public AbstractCard makeCopy() {
        return new Wish();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\purple\Wish.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


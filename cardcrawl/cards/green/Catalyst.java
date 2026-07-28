package com.megacrit.cardcrawl.cards.green;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.unique.DoublePoisonAction;
import com.megacrit.cardcrawl.actions.unique.TriplePoisonAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Catalyst extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Catalyst");
    public static final String ID = "Catalyst";

    public Catalyst() {
        super("Catalyst", cardStrings.NAME, "green/skill/catalyst", 1, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.GREEN, CardRarity.UNCOMMON, CardTarget.ENEMY);

        this.exhaust = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        if (!this.upgraded) {
            addToBot((AbstractGameAction) new DoublePoisonAction((AbstractCreature) m, (AbstractCreature) p));
        } else {
            addToBot((AbstractGameAction) new TriplePoisonAction((AbstractCreature) m, (AbstractCreature) p));
        }
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
            initializeDescription();
        }
    }

    public AbstractCard makeCopy() {
        return new Catalyst();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\green\
 * Catalyst.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


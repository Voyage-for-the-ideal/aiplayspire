package com.megacrit.cardcrawl.cards.green;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.unique.SkewerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Skewer extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Skewer");
    public static final String ID = "Skewer";

    public Skewer() {
        super("Skewer", cardStrings.NAME, "green/attack/skewer", -1, cardStrings.DESCRIPTION, CardType.ATTACK,
                CardColor.GREEN, CardRarity.UNCOMMON, CardTarget.ENEMY);

        this.baseDamage = 7;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new SkewerAction(p, m, this.damage, this.damageTypeForTurn, this.freeToPlayOnce,
                this.energyOnUse));
    }

    public AbstractCard makeCopy() {
        return new Skewer();
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(3);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\green\Skewer
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


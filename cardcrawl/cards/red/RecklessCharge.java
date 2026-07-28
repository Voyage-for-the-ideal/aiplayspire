package com.megacrit.cardcrawl.cards.red;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInDrawPileAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.cards.status.Dazed;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class RecklessCharge extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Reckless Charge");
    public static final String ID = "Reckless Charge";

    public RecklessCharge() {
        super("Reckless Charge", cardStrings.NAME, "red/attack/reckless_charge", 0, cardStrings.DESCRIPTION,
                CardType.ATTACK, CardColor.RED, CardRarity.UNCOMMON, CardTarget.ENEMY);

        this.baseDamage = 7;
        this.cardsToPreview = (AbstractCard) new Dazed();
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new DamageAction((AbstractCreature) m,
                new DamageInfo((AbstractCreature) p, this.damage, this.damageTypeForTurn),
                AbstractGameAction.AttackEffect.SLASH_HEAVY));
        addToBot((AbstractGameAction) new MakeTempCardInDrawPileAction((AbstractCard) new Dazed(), 1, true, true));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(3);
        }
    }

    public AbstractCard makeCopy() {
        return new RecklessCharge();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\red\
 * RecklessCharge.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


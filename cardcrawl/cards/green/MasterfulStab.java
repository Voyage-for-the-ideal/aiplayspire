package com.megacrit.cardcrawl.cards.green;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class MasterfulStab extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Masterful Stab");
    public static final String ID = "Masterful Stab";

    public MasterfulStab() {
        super("Masterful Stab", cardStrings.NAME, "green/attack/masterful_stab", 0, cardStrings.DESCRIPTION,
                CardType.ATTACK, CardColor.GREEN, CardRarity.UNCOMMON, CardTarget.ENEMY);

        this.baseDamage = 12;
    }

    public void tookDamage() {
        updateCost(1);
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new DamageAction((AbstractCreature) m,
                new DamageInfo((AbstractCreature) p, this.damage, this.damageTypeForTurn),
                AbstractGameAction.AttackEffect.SLASH_HEAVY));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(4);
        }
    }

    public AbstractCard makeCopy() {
        return new MasterfulStab();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\green\
 * MasterfulStab.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


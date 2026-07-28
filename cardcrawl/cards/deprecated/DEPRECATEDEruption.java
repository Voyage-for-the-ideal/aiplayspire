package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.watcher.ChangeStanceAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class DEPRECATEDEruption extends AbstractCard {
    public static final String ID = "Eruption";
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Eruption");

    public DEPRECATEDEruption() {
        super("Eruption", cardStrings.NAME, null, 2, cardStrings.DESCRIPTION, CardType.ATTACK, CardColor.PURPLE,
                CardRarity.UNCOMMON, CardTarget.ENEMY);

        this.baseDamage = 18;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new DamageAction((AbstractCreature) m,
                new DamageInfo((AbstractCreature) p, this.damage, this.damageTypeForTurn),
                AbstractGameAction.AttackEffect.FIRE));
        addToBot((AbstractGameAction) new ChangeStanceAction("Wrath"));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(7);
        }
    }

    public AbstractCard makeCopy() {
        return new DEPRECATEDEruption();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\deprecated\
 * DEPRECATEDEruption.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




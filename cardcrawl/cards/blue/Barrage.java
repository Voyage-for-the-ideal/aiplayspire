package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.defect.BarrageAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Barrage extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Barrage");
    public static final String ID = "Barrage";

    public Barrage() {
        super("Barrage", cardStrings.NAME, "blue/attack/barrage", 1, cardStrings.DESCRIPTION, CardType.ATTACK,
                CardColor.BLUE, CardRarity.COMMON, CardTarget.ENEMY);

        this.baseDamage = 4;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new BarrageAction((AbstractCreature) m,
                new DamageInfo((AbstractCreature) p, this.damage, DamageInfo.DamageType.NORMAL)));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(2);
        }
    }

    public AbstractCard makeCopy() {
        return new Barrage();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\blue\Barrage
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




package com.megacrit.cardcrawl.cards.purple;

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

public class Tantrum extends AbstractCard {
    public static final String ID = "Tantrum";
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Tantrum");

    public Tantrum() {
        super("Tantrum", cardStrings.NAME, "purple/attack/tantrum", 1, cardStrings.DESCRIPTION, CardType.ATTACK,
                CardColor.PURPLE, CardRarity.UNCOMMON, CardTarget.ENEMY);

        this.baseDamage = 3;
        this.baseMagicNumber = 3;
        this.magicNumber = 3;
        this.shuffleBackIntoDrawPile = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        for (int i = 0; i < this.magicNumber; i++) {
            addToBot((AbstractGameAction) new DamageAction((AbstractCreature) m,
                    new DamageInfo((AbstractCreature) p, this.damage, this.damageTypeForTurn),
                    AbstractGameAction.AttackEffect.BLUNT_LIGHT));
        }
        addToBot((AbstractGameAction) new ChangeStanceAction("Wrath"));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeMagicNumber(1);
        }
    }

    public AbstractCard makeCopy() {
        return new Tantrum();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\purple\
 * Tantrum.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


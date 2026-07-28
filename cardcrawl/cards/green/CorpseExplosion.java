package com.megacrit.cardcrawl.cards.green;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.CorpseExplosionPower;
import com.megacrit.cardcrawl.powers.PoisonPower;

public class CorpseExplosion extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Corpse Explosion");
    public static final String ID = "Corpse Explosion";

    public CorpseExplosion() {
        super("Corpse Explosion", cardStrings.NAME, "green/skill/corpse_explosion", 2, cardStrings.DESCRIPTION,
                CardType.SKILL, CardColor.GREEN, CardRarity.RARE, CardTarget.ENEMY);

        this.baseMagicNumber = 6;
        this.magicNumber = this.baseMagicNumber;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) m, (AbstractCreature) p,
                (AbstractPower) new PoisonPower((AbstractCreature) m, (AbstractCreature) p, this.magicNumber),
                this.magicNumber, AbstractGameAction.AttackEffect.POISON));
        addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) m, (AbstractCreature) p,
                (AbstractPower) new CorpseExplosionPower((AbstractCreature) m), 1,
                AbstractGameAction.AttackEffect.POISON));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeMagicNumber(3);
        }
    }

    public AbstractCard makeCopy() {
        return new CorpseExplosion();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\green\
 * CorpseExplosion.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


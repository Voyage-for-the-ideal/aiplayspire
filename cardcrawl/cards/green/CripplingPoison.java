package com.megacrit.cardcrawl.cards.green;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.PoisonPower;
import com.megacrit.cardcrawl.powers.WeakPower;

public class CripplingPoison extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Crippling Poison");
    public static final String ID = "Crippling Poison";

    public CripplingPoison() {
        super("Crippling Poison", cardStrings.NAME, "green/skill/crippling_poison", 2, cardStrings.DESCRIPTION,
                CardType.SKILL, CardColor.GREEN, CardRarity.UNCOMMON, CardTarget.ALL_ENEMY);

        this.baseMagicNumber = 4;
        this.magicNumber = this.baseMagicNumber;
        this.exhaust = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        if (!AbstractDungeon.getMonsters().areMonstersBasicallyDead()) {
            flash();
            for (AbstractMonster monster : (AbstractDungeon.getMonsters()).monsters) {
                if (!monster.isDead && !monster.isDying) {
                    addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) monster, (AbstractCreature) p,
                            (AbstractPower) new PoisonPower((AbstractCreature) monster, (AbstractCreature) p,
                                    this.magicNumber),
                            this.magicNumber));
                    addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) monster, (AbstractCreature) p,
                            (AbstractPower) new WeakPower((AbstractCreature) monster, 2, false), 2));
                }
            }
        }
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeMagicNumber(3);
        }
    }

    public AbstractCard makeCopy() {
        return new CripplingPoison();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\green\
 * CripplingPoison.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


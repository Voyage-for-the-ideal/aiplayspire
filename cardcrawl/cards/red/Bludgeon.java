package com.megacrit.cardcrawl.cards.red;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.WeightyImpactEffect;

public class Bludgeon extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Bludgeon");
    public static final String ID = "Bludgeon";

    public Bludgeon() {
        super("Bludgeon", cardStrings.NAME, "red/attack/bludgeon", 3, cardStrings.DESCRIPTION, CardType.ATTACK,
                CardColor.RED, CardRarity.RARE, CardTarget.ENEMY);

        this.baseDamage = 32;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        if (m != null) {
            addToBot(
                    (AbstractGameAction) new VFXAction((AbstractGameEffect) new WeightyImpactEffect(m.hb.cX, m.hb.cY)));
        }
        addToBot((AbstractGameAction) new WaitAction(0.8F));
        addToBot((AbstractGameAction) new DamageAction((AbstractCreature) m,
                new DamageInfo((AbstractCreature) p, this.damage, this.damageTypeForTurn),
                AbstractGameAction.AttackEffect.NONE));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(10);
        }
    }

    public AbstractCard makeCopy() {
        return new Bludgeon();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\red\Bludgeon
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


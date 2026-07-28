package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.defect.ChannelAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.orbs.Plasma;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.WeightyImpactEffect;

public class MeteorStrike extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Meteor Strike");
    public static final String ID = "Meteor Strike";

    public MeteorStrike() {
        super("Meteor Strike", cardStrings.NAME, "blue/attack/meteor_strike", 5, cardStrings.DESCRIPTION,
                CardType.ATTACK, CardColor.BLUE, CardRarity.RARE, CardTarget.ENEMY);

        this.baseDamage = 24;
        this.baseMagicNumber = 3;
        this.magicNumber = this.baseMagicNumber;
        this.tags.add(CardTags.STRIKE);
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

        for (int i = 0; i < this.magicNumber; i++) {
            addToBot((AbstractGameAction) new ChannelAction((AbstractOrb) new Plasma()));
        }
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(6);
        }
    }

    public AbstractCard makeCopy() {
        return new MeteorStrike();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\blue\
 * MeteorStrike.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




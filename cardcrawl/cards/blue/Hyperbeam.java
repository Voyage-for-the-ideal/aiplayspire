package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.FocusPower;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.MindblastEffect;

public class Hyperbeam extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Hyperbeam");
    public static final String ID = "Hyperbeam";

    public Hyperbeam() {
        super("Hyperbeam", cardStrings.NAME, "blue/attack/hyper_beam", 2, cardStrings.DESCRIPTION, CardType.ATTACK,
                CardColor.BLUE, CardRarity.RARE, CardTarget.ALL_ENEMY);

        this.baseDamage = 26;
        this.isMultiDamage = true;
        this.baseMagicNumber = 3;
        this.magicNumber = this.baseMagicNumber;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new SFXAction("ATTACK_HEAVY"));
        addToBot((AbstractGameAction) new VFXAction((AbstractCreature) p,
                (AbstractGameEffect) new MindblastEffect(p.dialogX, p.dialogY, p.flipHorizontal), 0.1F));
        addToBot((AbstractGameAction) new DamageAllEnemiesAction((AbstractCreature) p, this.multiDamage,
                this.damageTypeForTurn, AbstractGameAction.AttackEffect.NONE));
        addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) p, (AbstractCreature) p,
                (AbstractPower) new FocusPower((AbstractCreature) p, -this.magicNumber), -this.magicNumber));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(8);
        }
    }

    public AbstractCard makeCopy() {
        return new Hyperbeam();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\blue\
 * Hyperbeam.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




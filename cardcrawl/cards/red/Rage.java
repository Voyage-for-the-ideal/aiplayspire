package com.megacrit.cardcrawl.cards.red;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.RagePower;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.ShockWaveEffect;

public class Rage extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Rage");
    public static final String ID = "Rage";

    public Rage() {
        super("Rage", cardStrings.NAME, "red/skill/rage", 0, cardStrings.DESCRIPTION, CardType.SKILL, CardColor.RED,
                CardRarity.UNCOMMON, CardTarget.SELF);

        this.baseMagicNumber = 3;
        this.magicNumber = this.baseMagicNumber;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new SFXAction("RAGE"));
        addToBot((AbstractGameAction) new VFXAction((AbstractCreature) p,
                (AbstractGameEffect) new ShockWaveEffect(p.hb.cX, p.hb.cY, Color.ORANGE,
                        ShockWaveEffect.ShockWaveType.CHAOTIC),
                1.0F));
        addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) p, (AbstractCreature) p,
                (AbstractPower) new RagePower((AbstractCreature) p, this.magicNumber), this.magicNumber));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeMagicNumber(2);
        }
    }

    public AbstractCard makeCopy() {
        return new Rage();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\red\Rage.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


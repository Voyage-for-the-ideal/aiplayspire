package com.megacrit.cardcrawl.cards.red;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.actions.common.LoseHPAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.OfferingEffect;

public class Offering extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Offering");
    public static final String ID = "Offering";

    public Offering() {
        super("Offering", cardStrings.NAME, "red/skill/offering", 0, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.RED, CardRarity.RARE, CardTarget.SELF);

        this.exhaust = true;
        this.baseMagicNumber = 3;
        this.magicNumber = this.baseMagicNumber;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        if (Settings.FAST_MODE) {
            addToBot((AbstractGameAction) new VFXAction((AbstractGameEffect) new OfferingEffect(), 0.1F));
        } else {
            addToBot((AbstractGameAction) new VFXAction((AbstractGameEffect) new OfferingEffect(), 0.5F));
        }
        addToBot((AbstractGameAction) new LoseHPAction((AbstractCreature) p, (AbstractCreature) p, 6));
        addToBot((AbstractGameAction) new GainEnergyAction(2));
        addToBot((AbstractGameAction) new DrawCardAction((AbstractCreature) p, this.magicNumber));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeMagicNumber(2);
            this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
            initializeDescription();
        }
    }

    public AbstractCard makeCopy() {
        return new Offering();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\red\Offering
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


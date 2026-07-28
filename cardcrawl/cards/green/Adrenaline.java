package com.megacrit.cardcrawl.cards.green;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.AdrenalineEffect;

public class Adrenaline extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Adrenaline");
    public static final String ID = "Adrenaline";

    public Adrenaline() {
        super("Adrenaline", cardStrings.NAME, "green/skill/adrenaline", 0, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.GREEN, CardRarity.RARE, CardTarget.SELF);

        this.exhaust = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new VFXAction((AbstractGameEffect) new AdrenalineEffect(), 0.15F));
        if (this.upgraded) {
            addToBot((AbstractGameAction) new GainEnergyAction(2));
        } else {
            addToBot((AbstractGameAction) new GainEnergyAction(1));
        }
        addToBot((AbstractGameAction) new DrawCardAction((AbstractCreature) p, 2));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
            initializeDescription();
        }
    }

    public AbstractCard makeCopy() {
        return new Adrenaline();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\green\
 * Adrenaline.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




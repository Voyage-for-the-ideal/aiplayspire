package com.megacrit.cardcrawl.cards.tempCards;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.BorderFlashEffect;
import com.megacrit.cardcrawl.vfx.combat.MiracleEffect;

public class Miracle extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Miracle");
    public static final String ID = "Miracle";

    public Miracle() {
        super("Miracle", cardStrings.NAME, "colorless/skill/miracle", 0, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.COLORLESS, CardRarity.SPECIAL, CardTarget.NONE);

        this.exhaust = true;
        this.selfRetain = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        if (!Settings.DISABLE_EFFECTS) {
            addToBot((AbstractGameAction) new VFXAction(
                    (AbstractGameEffect) new BorderFlashEffect(Color.GOLDENROD, true)));
        }
        addToBot((AbstractGameAction) new VFXAction((AbstractGameEffect) new MiracleEffect()));
        if (this.upgraded) {
            addToBot((AbstractGameAction) new GainEnergyAction(2));
        } else {
            addToBot((AbstractGameAction) new GainEnergyAction(1));
        }
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
            initializeDescription();
        }
    }

    public AbstractCard makeCopy() {
        return new Miracle();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\tempCards\
 * Miracle.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


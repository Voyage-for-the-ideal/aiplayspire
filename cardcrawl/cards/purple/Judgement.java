package com.megacrit.cardcrawl.cards.purple;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.actions.watcher.JudgementAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.GiantTextEffect;
import com.megacrit.cardcrawl.vfx.combat.WeightyImpactEffect;

public class Judgement extends AbstractCard {
    public static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Judgement");
    public static final String ID = "Judgement";

    public Judgement() {
        super("Judgement", cardStrings.NAME, "purple/skill/judgment", 1, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.PURPLE, CardRarity.RARE, CardTarget.ENEMY);

        this.baseMagicNumber = 30;
        this.magicNumber = 30;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        if (m != null) {
            addToBot((AbstractGameAction) new VFXAction(
                    (AbstractGameEffect) new WeightyImpactEffect(m.hb.cX, m.hb.cY, Color.GOLD.cpy())));
            addToBot((AbstractGameAction) new WaitAction(0.8F));
            addToBot((AbstractGameAction) new VFXAction((AbstractGameEffect) new GiantTextEffect(m.hb.cX, m.hb.cY)));
        }
        addToBot((AbstractGameAction) new JudgementAction((AbstractCreature) m, this.magicNumber));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeMagicNumber(10);
        }
    }

    public AbstractCard makeCopy() {
        return new Judgement();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\purple\
 * Judgement.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


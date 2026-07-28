package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction;
import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.actions.watcher.StanceCheckAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class DEPRECATEDFlare extends AbstractCard {
    public static final String ID = "Flare";
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Flare");

    public DEPRECATEDFlare() {
        super("Flare", cardStrings.NAME, null, 2, cardStrings.DESCRIPTION, CardType.ATTACK, CardColor.PURPLE,
                CardRarity.COMMON, CardTarget.ALL_ENEMY);

        this.baseDamage = 9;
        this.isMultiDamage = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new DamageAllEnemiesAction((AbstractCreature) p, this.multiDamage,
                this.damageTypeForTurn, AbstractGameAction.AttackEffect.FIRE));

        addToBot((AbstractGameAction) new StanceCheckAction("Wrath", (AbstractGameAction) new GainEnergyAction(2)));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(3);
        }
    }

    public void triggerOnGlowCheck() {
        if (this.isGlowing) {
            this

                    .glowColor = AbstractDungeon.player.stance.ID.equals("Wrath")
                            ? AbstractCard.GOLD_BORDER_GLOW_COLOR.cpy()
                            : AbstractCard.BLUE_BORDER_GLOW_COLOR.cpy();
        }
    }

    public AbstractCard makeCopy() {
        return new DEPRECATEDFlare();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\deprecated\
 * DEPRECATEDFlare.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.VFXAction;
import com.megacrit.cardcrawl.actions.common.DamageAllEnemiesAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.vfx.AbstractGameEffect;
import com.megacrit.cardcrawl.vfx.combat.BlizzardEffect;

public class Blizzard extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Blizzard");
    public static final String ID = "Blizzard";

    public Blizzard() {
        super("Blizzard", cardStrings.NAME, "blue/attack/blizzard", 1, cardStrings.DESCRIPTION, CardType.ATTACK,
                CardColor.BLUE, CardRarity.UNCOMMON, CardTarget.ALL_ENEMY);

        this.baseDamage = 0;
        this.baseMagicNumber = 2;
        this.magicNumber = this.baseMagicNumber;
        this.isMultiDamage = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        int frostCount = 0;
        for (AbstractOrb o : AbstractDungeon.actionManager.orbsChanneledThisCombat) {
            if (o instanceof com.megacrit.cardcrawl.orbs.Frost) {
                frostCount++;
            }
        }

        this.baseDamage = frostCount * this.magicNumber;
        calculateCardDamage((AbstractMonster) null);

        if (Settings.FAST_MODE) {
            addToBot((AbstractGameAction) new VFXAction((AbstractGameEffect) new BlizzardEffect(frostCount,
                    AbstractDungeon.getMonsters().shouldFlipVfx()), 0.25F));
        } else {
            addToBot((AbstractGameAction) new VFXAction(
                    (AbstractGameEffect) new BlizzardEffect(frostCount, AbstractDungeon.getMonsters().shouldFlipVfx()),
                    1.0F));
        }

        addToBot((AbstractGameAction) new DamageAllEnemiesAction((AbstractCreature) p, this.multiDamage,
                this.damageTypeForTurn, AbstractGameAction.AttackEffect.BLUNT_HEAVY, false));
    }

    public void applyPowers() {
        int frostCount = 0;
        for (AbstractOrb o : AbstractDungeon.actionManager.orbsChanneledThisCombat) {
            if (o instanceof com.megacrit.cardcrawl.orbs.Frost) {
                frostCount++;
            }
        }

        if (frostCount > 0) {
            this.baseDamage = frostCount * this.magicNumber;
            super.applyPowers();
            this.rawDescription = cardStrings.DESCRIPTION + cardStrings.EXTENDED_DESCRIPTION[0];
            initializeDescription();
        }
    }

    public void onMoveToDiscard() {
        this.rawDescription = cardStrings.DESCRIPTION;
        initializeDescription();
    }

    public void calculateCardDamage(AbstractMonster mo) {
        super.calculateCardDamage(mo);
        this.rawDescription = cardStrings.DESCRIPTION;
        this.rawDescription += cardStrings.EXTENDED_DESCRIPTION[0];
        initializeDescription();
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeMagicNumber(1);
        }
    }

    public AbstractCard makeCopy() {
        return new Blizzard();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\blue\
 * Blizzard.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




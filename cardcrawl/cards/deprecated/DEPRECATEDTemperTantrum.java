package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.unique.SwordBoomerangAction;
import com.megacrit.cardcrawl.actions.watcher.StanceCheckAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class DEPRECATEDTemperTantrum extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("TemperTantrum");
    public static final String ID = "TemperTantrum";

    public DEPRECATEDTemperTantrum() {
        super("TemperTantrum", cardStrings.NAME, "red/attack/sword_boomerang", 1, cardStrings.DESCRIPTION,
                CardType.ATTACK, CardColor.PURPLE, CardRarity.COMMON, CardTarget.ALL_ENEMY);

        this.baseDamage = 6;
        this.magicNumber = this.baseMagicNumber;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new SwordBoomerangAction(

                (AbstractCreature) AbstractDungeon.getMonsters().getRandomMonster(null, true,
                        AbstractDungeon.cardRandomRng),
                new DamageInfo((AbstractCreature) p, this.baseDamage), 1));

        addToBot((AbstractGameAction) new StanceCheckAction("Wrath",
                (AbstractGameAction) new SwordBoomerangAction(new DamageInfo((AbstractCreature) p, this.baseDamage),
                        1)));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(3);
        }
    }

    public AbstractCard makeCopy() {
        return new DEPRECATEDTemperTantrum();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\deprecated\
 * DEPRECATEDTemperTantrum.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




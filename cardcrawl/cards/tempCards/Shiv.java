package com.megacrit.cardcrawl.cards.tempCards;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Shiv extends AbstractCard {
    public static final String ID = "Shiv";
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Shiv");
    public static final int ATTACK_DMG = 4;

    public Shiv() {
        super("Shiv", cardStrings.NAME, "colorless/attack/shiv", 0, cardStrings.DESCRIPTION, CardType.ATTACK,
                CardColor.COLORLESS, CardRarity.SPECIAL, CardTarget.ENEMY);

        if (AbstractDungeon.player != null && AbstractDungeon.player.hasPower("Accuracy")) {
            this.baseDamage = 4 + (AbstractDungeon.player.getPower("Accuracy")).amount;
        } else {
            this.baseDamage = 4;
        }
        this.exhaust = true;
    }
    public static final int UPG_ATTACK_DMG = 2;

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new DamageAction((AbstractCreature) m,
                new DamageInfo((AbstractCreature) p, this.damage, this.damageTypeForTurn),
                AbstractGameAction.AttackEffect.SLASH_HORIZONTAL));
    }

    public AbstractCard makeCopy() {
        return new Shiv();
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(2);
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\tempCards\
 * Shiv.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


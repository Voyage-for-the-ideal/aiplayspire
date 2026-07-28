package com.megacrit.cardcrawl.cards.purple;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInHandAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.cards.tempCards.Smite;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class CarveReality extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("CarveReality");
    public static final String ID = "CarveReality";

    public CarveReality() {
        super("CarveReality", cardStrings.NAME, "purple/attack/carve_reality", 1, cardStrings.DESCRIPTION,
                CardType.ATTACK, CardColor.PURPLE, CardRarity.UNCOMMON, CardTarget.ENEMY);

        this.baseDamage = 6;
        this.cardsToPreview = (AbstractCard) new Smite();
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new DamageAction((AbstractCreature) m,
                new DamageInfo((AbstractCreature) p, this.damage, this.damageTypeForTurn),
                AbstractGameAction.AttackEffect.SLASH_HEAVY));
        addToBot((AbstractGameAction) new MakeTempCardInHandAction(this.cardsToPreview.makeStatEquivalentCopy(), 1));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeDamage(4);
        }
    }

    public AbstractCard makeCopy() {
        return new CarveReality();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\purple\
 * CarveReality.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


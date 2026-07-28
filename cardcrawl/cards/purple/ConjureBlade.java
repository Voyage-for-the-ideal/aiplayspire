package com.megacrit.cardcrawl.cards.purple;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.watcher.ConjureBladeAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.tempCards.Expunger;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class ConjureBlade extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("ConjureBlade");
    public static final String ID = "ConjureBlade";

    public ConjureBlade() {
        super("ConjureBlade", cardStrings.NAME, "purple/skill/conjure_blade", -1, cardStrings.DESCRIPTION,
                CardType.SKILL, CardColor.PURPLE, CardRarity.RARE, CardTarget.SELF);

        this.cardsToPreview = (AbstractCard) new Expunger();
        this.exhaust = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        if (this.upgraded) {
            addToBot((AbstractGameAction) new ConjureBladeAction(p, this.freeToPlayOnce, this.energyOnUse + 1));
        } else {
            addToBot((AbstractGameAction) new ConjureBladeAction(p, this.freeToPlayOnce, this.energyOnUse));
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
        return new ConjureBlade();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\purple\
 * ConjureBlade.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


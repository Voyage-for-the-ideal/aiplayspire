package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.watcher.DivinePunishmentAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.tempCards.Smite;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class DEPRECATEDCleanseEvil extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("CleanseEvil");
    public static final String ID = "CleanseEvil";

    public DEPRECATEDCleanseEvil() {
        super("CleanseEvil", cardStrings.NAME, "purple/skill/cleanse_evil", -1, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.PURPLE, CardRarity.UNCOMMON, CardTarget.SELF);

        this.cardsToPreview = (AbstractCard) new Smite();
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        Smite smite = new Smite();
        if (this.upgraded) {
            smite.upgrade();
        }
        addToBot((AbstractGameAction) new DivinePunishmentAction((AbstractCard) smite, this.freeToPlayOnce,
                this.energyOnUse));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            this.cardsToPreview.upgrade();
            this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
            initializeDescription();
        }
    }

    public AbstractCard makeCopy() {
        return new DEPRECATEDCleanseEvil();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\deprecated\
 * DEPRECATEDCleanseEvil.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




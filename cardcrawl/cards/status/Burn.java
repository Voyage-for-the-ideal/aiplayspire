package com.megacrit.cardcrawl.cards.status;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DamageAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Burn extends AbstractCard {
    public static final String ID = "Burn";
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Burn");

    public Burn() {
        super("Burn", cardStrings.NAME, "status/burn", -2, cardStrings.DESCRIPTION, CardType.STATUS,
                CardColor.COLORLESS, CardRarity.COMMON, CardTarget.NONE);

        this.magicNumber = 2;
        this.baseMagicNumber = 2;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        if (this.dontTriggerOnUseCard) {
            addToBot((AbstractGameAction) new DamageAction((AbstractCreature) AbstractDungeon.player,
                    new DamageInfo((AbstractCreature) AbstractDungeon.player, this.magicNumber,
                            DamageInfo.DamageType.THORNS),
                    AbstractGameAction.AttackEffect.FIRE));
        }
    }

    public void triggerOnEndOfTurnForPlayingCard() {
        this.dontTriggerOnUseCard = true;
        AbstractDungeon.actionManager.cardQueue.add(new CardQueueItem(this, true));
    }

    public AbstractCard makeCopy() {
        Burn retVal = new Burn();
        return retVal;
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeMagicNumber(2);
            this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
            initializeDescription();
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\status\Burn.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


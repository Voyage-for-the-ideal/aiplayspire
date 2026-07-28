package com.megacrit.cardcrawl.cards.curses;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.LoseHPAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Pain extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Pain");
    public static final String ID = "Pain";

    public Pain() {
        super("Pain", cardStrings.NAME, "curse/pain", -2, cardStrings.DESCRIPTION, CardType.CURSE, CardColor.CURSE,
                CardRarity.CURSE, CardTarget.NONE);
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
    }

    public void triggerOnOtherCardPlayed(AbstractCard c) {
        addToTop((AbstractGameAction) new LoseHPAction((AbstractCreature) AbstractDungeon.player,
                (AbstractCreature) AbstractDungeon.player, 1));
    }

    public void upgrade() {
    }

    public AbstractCard makeCopy() {
        return new Pain();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\curses\Pain.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




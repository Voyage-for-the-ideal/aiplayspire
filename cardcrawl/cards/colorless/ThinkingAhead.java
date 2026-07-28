package com.megacrit.cardcrawl.cards.colorless;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.actions.common.PutOnDeckAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class ThinkingAhead extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Thinking Ahead");
    public static final String ID = "Thinking Ahead";

    public ThinkingAhead() {
        super("Thinking Ahead", cardStrings.NAME, "colorless/skill/thinking_ahead", 0, cardStrings.DESCRIPTION,
                CardType.SKILL, CardColor.COLORLESS, CardRarity.RARE, CardTarget.NONE);

        this.exhaust = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new DrawCardAction((AbstractCreature) p, 2));
        if (AbstractDungeon.player.hand.size() > 0) {
            addToBot((AbstractGameAction) new PutOnDeckAction((AbstractCreature) p, (AbstractCreature) p, 1, false));
        }
    }

    public AbstractCard makeCopy() {
        return new ThinkingAhead();
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            this.exhaust = false;
            this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
            initializeDescription();
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\colorless\
 * ThinkingAhead.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




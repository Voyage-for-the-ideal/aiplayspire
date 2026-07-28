package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.PlayTopCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class DEPRECATEDPathToVictory extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("PathToVictory");
    public static final String ID = "PathToVictory";

    public DEPRECATEDPathToVictory() {
        super("PathToVictory", cardStrings.NAME, "purple/skill/path_to_victory", 1, cardStrings.DESCRIPTION,
                CardType.SKILL, CardColor.PURPLE, CardRarity.UNCOMMON, CardTarget.NONE);

        this.exhaust = true;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new PlayTopCardAction(

                (AbstractCreature) (AbstractDungeon.getCurrRoom()).monsters.getRandomMonster(null, true,
                        AbstractDungeon.cardRandomRng),
                false));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            this.exhaust = false;
            this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
            initializeDescription();
        }
    }

    public AbstractCard makeCopy() {
        return new DEPRECATEDPathToVictory();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\deprecated\
 * DEPRECATEDPathToVictory.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */




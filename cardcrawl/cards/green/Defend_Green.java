package com.megacrit.cardcrawl.cards.green;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Defend_Green extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Defend_G");
    public static final String ID = "Defend_G";

    public Defend_Green() {
        super("Defend_G", cardStrings.NAME, "green/skill/defend", 1, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.GREEN, CardRarity.BASIC, CardTarget.SELF);

        this.baseBlock = 5;
        this.tags.add(CardTags.STARTER_DEFEND);
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        if (Settings.isDebug) {
            addToBot((AbstractGameAction) new GainBlockAction((AbstractCreature) p, (AbstractCreature) p, 50));
        } else {
            addToBot((AbstractGameAction) new GainBlockAction((AbstractCreature) p, (AbstractCreature) p, this.block));
        }
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeBlock(3);
        }
    }

    public AbstractCard makeCopy() {
        return new Defend_Green();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\green\
 * Defend_Green.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


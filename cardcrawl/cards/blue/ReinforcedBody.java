package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.defect.ReinforcedBodyAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class ReinforcedBody extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Reinforced Body");
    public static final String ID = "Reinforced Body";

    public ReinforcedBody() {
        super("Reinforced Body", cardStrings.NAME, "blue/skill/reinforced_body", -1, cardStrings.DESCRIPTION,
                CardType.SKILL, CardColor.BLUE, CardRarity.UNCOMMON, CardTarget.SELF);

        this.baseBlock = 7;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new ReinforcedBodyAction(p, this.block, this.freeToPlayOnce, this.energyOnUse));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeBlock(2);
        }
    }

    public AbstractCard makeCopy() {
        return new ReinforcedBody();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\blue\
 * ReinforcedBody.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




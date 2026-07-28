package com.megacrit.cardcrawl.cards.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class DEPRECATEDWardAura extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("WardAura");
    public static final String ID = "WardAura";

    public DEPRECATEDWardAura() {
        super("WardAura", cardStrings.NAME, null, 1, cardStrings.DESCRIPTION, CardType.SKILL, CardColor.PURPLE,
                CardRarity.UNCOMMON, CardTarget.SELF);

        this.selfRetain = true;
        this.baseBlock = 8;
        this.magicNumber = 2;
        this.baseMagicNumber = 2;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        addToBot((AbstractGameAction) new GainBlockAction((AbstractCreature) p, (AbstractCreature) p, this.block));
    }

    public void atTurnStartPreDraw() {
        addToBot((AbstractGameAction) new GainBlockAction((AbstractCreature) AbstractDungeon.player,
                (AbstractCreature) AbstractDungeon.player, this.magicNumber));
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeMagicNumber(1);
            upgradeBlock(3);
        }
    }

    public AbstractCard makeCopy() {
        return new DEPRECATEDWardAura();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\deprecated\
 * DEPRECATEDWardAura.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




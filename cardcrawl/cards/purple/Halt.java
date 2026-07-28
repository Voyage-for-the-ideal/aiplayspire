package com.megacrit.cardcrawl.cards.purple;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.watcher.HaltAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

public class Halt extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Halt");
    public static final String ID = "Halt";
    private static final int BLOCK_AMOUNT = 3;
    private static final int UPGRADE_PLUS_BLOCK = 1;
    private static final int BLOCK_DIFFERENCE = 6;
    private static final int UPGRADE_PLUS_BLOCK_DIFFERENCE = 4;

    public Halt() {
        super("Halt", cardStrings.NAME, "purple/skill/halt", 0, cardStrings.DESCRIPTION, CardType.SKILL,
                CardColor.PURPLE, CardRarity.COMMON, CardTarget.SELF);

        this.block = this.baseBlock = 3;
        this.magicNumber = this.baseMagicNumber = 9;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        applyPowers();
        addToBot((AbstractGameAction) new HaltAction((AbstractCreature) p, this.block, this.magicNumber));
    }

    public void applyPowers() {
        this.baseBlock += 6 + this.timesUpgraded * 4;
        this.baseMagicNumber = this.baseBlock;
        super.applyPowers();
        this.magicNumber = this.block;
        this.isMagicNumberModified = this.isBlockModified;
        this.baseBlock -= 6 + this.timesUpgraded * 4;
        super.applyPowers();
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            upgradeBlock(1);
            this.baseMagicNumber = this.baseBlock + 6 + this.timesUpgraded * 4;

            this.upgradedMagicNumber = this.upgradedBlock;
        }
    }

    public AbstractCard makeCopy() {
        return new Halt();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\purple\Halt.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


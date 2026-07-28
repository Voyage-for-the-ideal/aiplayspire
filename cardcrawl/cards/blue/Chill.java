package com.megacrit.cardcrawl.cards.blue;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.defect.ChannelAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.CardStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.orbs.Frost;

public class Chill extends AbstractCard {
    private static final CardStrings cardStrings = CardCrawlGame.languagePack.getCardStrings("Chill");
    public static final String ID = "Chill";

    public Chill() {
        super("Chill", cardStrings.NAME, "blue/skill/chill", 0, cardStrings.DESCRIPTION, CardType.SKILL, CardColor.BLUE,
                CardRarity.UNCOMMON, CardTarget.SELF);

        this.exhaust = true;
        this.showEvokeValue = true;
        this.showEvokeOrbCount = 3;
        this.baseMagicNumber = 1;
        this.magicNumber = this.baseMagicNumber;
    }

    public void use(AbstractPlayer p, AbstractMonster m) {
        int count = 0;
        for (AbstractMonster mon : (AbstractDungeon.getMonsters()).monsters) {
            if (!mon.isDeadOrEscaped()) {
                count++;
            }
        }
        for (int i = 0; i < count * this.magicNumber; i++) {
            addToBot((AbstractGameAction) new ChannelAction((AbstractOrb) new Frost()));
        }
    }

    public void upgrade() {
        if (!this.upgraded) {
            upgradeName();
            this.isInnate = true;
            this.rawDescription = cardStrings.UPGRADE_DESCRIPTION;
            initializeDescription();
        }
    }

    public AbstractCard makeCopy() {
        return new Chill();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\cards\blue\Chill.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class AccuracyPower
        extends AbstractPower {
    public static final String POWER_ID = "Accuracy";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Accuracy");
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public AccuracyPower(AbstractCreature owner, int amt) {
        this.name = powerStrings.NAME;
        this.ID = "Accuracy";
        this.owner = owner;
        this.amount = amt;
        updateDescription();
        loadRegion("accuracy");
        updateExistingShivs();
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
    }

    public void stackPower(int stackAmount) {
        this.fontScale = 8.0F;
        this.amount += stackAmount;
        updateExistingShivs();
    }

    private void updateExistingShivs() {
        for (AbstractCard c : AbstractDungeon.player.hand.group) {
            if (c instanceof com.megacrit.cardcrawl.cards.tempCards.Shiv) {
                if (!c.upgraded) {
                    c.baseDamage = 4 + this.amount;
                    continue;
                }
                c.baseDamage = 6 + this.amount;
            }
        }

        for (AbstractCard c : AbstractDungeon.player.drawPile.group) {
            if (c instanceof com.megacrit.cardcrawl.cards.tempCards.Shiv) {
                if (!c.upgraded) {
                    c.baseDamage = 4 + this.amount;
                    continue;
                }
                c.baseDamage = 6 + this.amount;
            }
        }

        for (AbstractCard c : AbstractDungeon.player.discardPile.group) {
            if (c instanceof com.megacrit.cardcrawl.cards.tempCards.Shiv) {
                if (!c.upgraded) {
                    c.baseDamage = 4 + this.amount;
                    continue;
                }
                c.baseDamage = 6 + this.amount;
            }
        }

        for (AbstractCard c : AbstractDungeon.player.exhaustPile.group) {
            if (c instanceof com.megacrit.cardcrawl.cards.tempCards.Shiv) {
                if (!c.upgraded) {
                    c.baseDamage = 4 + this.amount;
                    continue;
                }
                c.baseDamage = 6 + this.amount;
            }
        }
    }

    public void onDrawOrDiscard() {
        for (AbstractCard c : AbstractDungeon.player.hand.group) {
            if (c instanceof com.megacrit.cardcrawl.cards.tempCards.Shiv) {
                if (!c.upgraded) {
                    c.baseDamage = 4 + this.amount;
                    continue;
                }
                c.baseDamage = 6 + this.amount;
            }
        }
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * AccuracyPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


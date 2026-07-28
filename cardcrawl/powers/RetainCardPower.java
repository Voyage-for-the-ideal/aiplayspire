package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.unique.RetainCardsAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class RetainCardPower extends AbstractPower {
    public static final String POWER_ID = "Retain Cards";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Retain Cards");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public RetainCardPower(AbstractCreature owner, int numCards) {
        this.name = NAME;
        this.ID = "Retain Cards";
        this.owner = owner;
        this.amount = numCards;
        updateDescription();
        loadRegion("retain");
    }

    public void updateDescription() {
        if (this.amount == 1) {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
        } else {
            this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[2];
        }
    }

    public void atEndOfTurn(boolean isPlayer) {
        if (isPlayer && !AbstractDungeon.player.hand.isEmpty() && !AbstractDungeon.player.hasRelic("Runic Pyramid") &&
                !AbstractDungeon.player.hasPower("Equilibrium"))
            addToBot((AbstractGameAction) new RetainCardsAction(this.owner, this.amount));
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * RetainCardPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


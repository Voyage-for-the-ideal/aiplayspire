package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class ConfusionPower extends AbstractPower {
    public static final String POWER_ID = "Confusion";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Confusion");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public ConfusionPower(AbstractCreature owner) {
        this.name = NAME;
        this.ID = "Confusion";
        this.owner = owner;
        updateDescription();
        loadRegion("confusion");
        this.type = PowerType.DEBUFF;
        this.priority = 0;
    }

    public void playApplyPowerSfx() {
        CardCrawlGame.sound.play("POWER_CONFUSION", 0.05F);
    }

    public void onCardDraw(AbstractCard card) {
        if (card.cost >= 0) {
            int newCost = AbstractDungeon.cardRandomRng.random(3);
            if (card.cost != newCost) {
                card.cost = newCost;
                card.costForTurn = card.cost;
                card.isCostModified = true;
            }
            card.freeToPlayOnce = false;
        }
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0];
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * ConfusionPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


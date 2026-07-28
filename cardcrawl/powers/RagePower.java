package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class RagePower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Rage");
    public static final String POWER_ID = "Rage";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public RagePower(AbstractCreature owner, int amount) {
        this.name = NAME;
        this.ID = "Rage";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("anger");
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
    }

    public void onUseCard(AbstractCard card, UseCardAction action) {
        if (card.type == AbstractCard.CardType.ATTACK) {
            addToBot((AbstractGameAction) new GainBlockAction((AbstractCreature) AbstractDungeon.player,
                    (AbstractCreature) AbstractDungeon.player, this.amount));
            flash();
        }
    }

    public void atEndOfTurn(boolean isPlayer) {
        addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "Rage"));
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\RagePower.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


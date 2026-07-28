package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class RepairPower extends AbstractPower {
    public static final String POWER_ID = "Repair";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Repair");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public RepairPower(AbstractCreature owner, int amount) {
        this.name = NAME;
        this.ID = "Repair";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("repair");
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
    }

    public void onVictory() {
        AbstractPlayer p = AbstractDungeon.player;
        if (p.currentHealth > 0)
            p.heal(this.amount);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\RepairPower
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


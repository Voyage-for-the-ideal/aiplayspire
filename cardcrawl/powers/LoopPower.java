package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.orbs.AbstractOrb;

public class LoopPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Loop");
    public static final String POWER_ID = "Loop";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public LoopPower(AbstractCreature owner, int amt) {
        this.name = NAME;
        this.ID = "Loop";
        this.owner = owner;
        this.amount = amt;
        updateDescription();
        loadRegion("loop");
    }

    public void atStartOfTurn() {
        if (!AbstractDungeon.player.orbs.isEmpty()) {
            flash();
            for (int i = 0; i < this.amount; i++) {
                ((AbstractOrb) AbstractDungeon.player.orbs.get(0)).onStartOfTurn();
                ((AbstractOrb) AbstractDungeon.player.orbs.get(0)).onEndOfTurn();
            }
        }
    }

    public void updateDescription() {
        if (this.amount <= 1) {
            this.description = DESCRIPTIONS[0];
        } else {
            this.description = DESCRIPTIONS[1] + this.amount + DESCRIPTIONS[2];
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\LoopPower.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class OmnisciencePower extends AbstractPower {
    public static final String POWER_ID = "OmnisciencePower";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("OmnisciencePower");

    public OmnisciencePower(AbstractCreature owner, int newAmount) {
        this.name = powerStrings.NAME;
        this.ID = "OmnisciencePower";
        this.owner = owner;
        this.amount = newAmount;
        updateDescription();
        loadRegion("devotion");
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * OmnisciencePower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


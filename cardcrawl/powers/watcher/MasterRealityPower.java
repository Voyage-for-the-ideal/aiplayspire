package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class MasterRealityPower extends AbstractPower {
    public static final String POWER_ID = "MasterRealityPower";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("MasterRealityPower");

    public MasterRealityPower(AbstractCreature owner) {
        this.name = powerStrings.NAME;
        this.ID = "MasterRealityPower";
        this.owner = owner;
        updateDescription();
        loadRegion("master_reality");
        this.type = PowerType.BUFF;
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0];
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * MasterRealityPower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


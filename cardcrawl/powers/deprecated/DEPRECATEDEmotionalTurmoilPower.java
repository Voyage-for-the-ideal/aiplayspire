package com.megacrit.cardcrawl.powers.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.deprecated.DEPRECATEDRandomStanceAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class DEPRECATEDEmotionalTurmoilPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack
            .getPowerStrings("EmotionalTurmoilPower");
    public static final String POWER_ID = "EmotionalTurmoilPower";

    public DEPRECATEDEmotionalTurmoilPower(AbstractCreature owner) {
        this.name = powerStrings.NAME;
        this.ID = "EmotionalTurmoilPower";
        this.owner = owner;
        updateDescription();
        loadRegion("draw");
        this.type = PowerType.BUFF;
        this.isTurnBased = false;
    }

    public void atStartOfTurnPostDraw() {
        addToBot((AbstractGameAction) new DEPRECATEDRandomStanceAction());
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0];
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\deprecated\
 * DEPRECATEDEmotionalTurmoilPower.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */


package com.megacrit.cardcrawl.powers.deprecated;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;

public class DEPRECATEDDisciplinePower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("DisciplinePower");
    public static final String POWER_ID = "DisciplinePower";

    public DEPRECATEDDisciplinePower(AbstractCreature owner) {
        this.name = powerStrings.NAME;
        this.ID = "DisciplinePower";
        this.owner = owner;
        updateDescription();
        loadRegion("no_stance");
        this.type = PowerType.BUFF;
        this.amount = -1;
    }

    public void atEndOfTurn(boolean isPlayer) {
        if (EnergyPanel.totalCount > 0) {
            this.amount = EnergyPanel.totalCount;
            this.fontScale = 8.0F;
        }
    }

    public void atStartOfTurn() {
        if (this.amount != -1) {
            addToTop((AbstractGameAction) new DrawCardAction(this.amount));
            this.amount = -1;
            this.fontScale = 8.0F;
            flash();
        }
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0];
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\deprecated\
 * DEPRECATEDDisciplinePower.class Java compiler version: 8 (52.0) JD-Core
 * Version: 1.1.3
 */


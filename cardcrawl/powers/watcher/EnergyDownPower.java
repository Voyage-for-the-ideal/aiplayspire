package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.unique.LoseEnergyAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.LocalizedStrings;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class EnergyDownPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("EnergyDownPower");
    public static final String POWER_ID = "EnergyDownPower";

    public EnergyDownPower(AbstractCreature owner, int amount, boolean isFasting) {
        this.name = powerStrings.NAME;
        this.ID = "EnergyDownPower";
        this.owner = owner;
        this.amount = amount;
        updateDescription();

        if (isFasting) {
            loadRegion("fasting");
        } else {
            loadRegion("energized_blue");
        }

        this.type = PowerType.DEBUFF;
    }

    public EnergyDownPower(AbstractCreature owner, int amount) {
        this(owner, amount, false);
    }

    public void updateDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(powerStrings.DESCRIPTIONS[0]);
        for (int i = 0; i < this.amount; i++) {
            sb.append("[E] ");
        }
        if (powerStrings.DESCRIPTIONS[1].isEmpty()) {
            sb.append(LocalizedStrings.PERIOD);
        } else {
            sb.append(powerStrings.DESCRIPTIONS[1]);
        }
        this.description = sb.toString();
    }

    public void atStartOfTurn() {
        addToBot((AbstractGameAction) new LoseEnergyAction(this.amount));
        flash();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * EnergyDownPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


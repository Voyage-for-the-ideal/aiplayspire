package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.DrawCardNextTurnPower;
import com.megacrit.cardcrawl.powers.EnergizedPower;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;

public class DoppelgangerAction
        extends AbstractGameAction {
    private boolean freeToPlayOnce;
    private boolean upgraded;

    public DoppelgangerAction(AbstractPlayer p, boolean upgraded, boolean freeToPlayOnce, int energyOnUse) {
        this.p = p;
        this.upgraded = upgraded;
        this.freeToPlayOnce = freeToPlayOnce;
        this.duration = Settings.ACTION_DUR_XFAST;
        this.actionType = ActionType.SPECIAL;
        this.energyOnUse = energyOnUse;
    }
    private AbstractPlayer p;
    private int energyOnUse;

    public void update() {
        int effect = EnergyPanel.totalCount;
        if (this.energyOnUse != -1) {
            effect = this.energyOnUse;
        }

        if (this.p.hasRelic("Chemical X")) {
            effect += 2;
            this.p.getRelic("Chemical X").flash();
        }

        if (this.upgraded) {
            effect++;
        }

        if (effect > 0) {
            addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this.p, (AbstractCreature) this.p,
                    (AbstractPower) new EnergizedPower((AbstractCreature) this.p, effect), effect));
            addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this.p, (AbstractCreature) this.p,
                    (AbstractPower) new DrawCardNextTurnPower((AbstractCreature) this.p, effect), effect));

            if (!this.freeToPlayOnce) {
                this.p.energy.use(EnergyPanel.totalCount);
            }
        }
        this.isDone = true;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * DoppelgangerAction.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */




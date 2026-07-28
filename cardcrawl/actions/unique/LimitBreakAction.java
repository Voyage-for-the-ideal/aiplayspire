package com.megacrit.cardcrawl.actions.unique;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.StrengthPower;

public class LimitBreakAction
        extends AbstractGameAction {
    private AbstractPlayer p = AbstractDungeon.player;

    public void update() {
        if (this.duration == Settings.ACTION_DUR_XFAST &&
                this.p.hasPower("Strength")) {
            int strAmt = (this.p.getPower("Strength")).amount;
            addToTop((AbstractGameAction) new ApplyPowerAction((AbstractCreature) this.p, (AbstractCreature) this.p,
                    (AbstractPower) new StrengthPower((AbstractCreature) this.p, strAmt), strAmt));
        }

        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\action\\unique\
 * LimitBreakAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




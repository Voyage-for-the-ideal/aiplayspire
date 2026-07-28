package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.LoseHPAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class MarkPower
        extends AbstractPower {
    public static final String POWER_ID = "PathToVictoryPower";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("PathToVictoryPower");

    public MarkPower(AbstractCreature owner, int amt) {
        this.name = powerStrings.NAME;
        this.ID = "PathToVictoryPower";
        this.owner = owner;
        this.amount = amt;
        updateDescription();
        loadRegion("pressure_points");
        this.type = PowerType.DEBUFF;
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
    }

    public void triggerMarks(AbstractCard card) {
        if (card.cardID.equals("PathToVictory"))
            addToBot((AbstractGameAction) new LoseHPAction(this.owner, null, this.amount,
                    AbstractGameAction.AttackEffect.FIRE));
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * MarkPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


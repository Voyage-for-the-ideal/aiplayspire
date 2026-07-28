package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.MakeTempCardInDrawPileAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.tempCards.Insight;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class StudyPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Study");
    public static final String POWER_ID = "Study";

    public StudyPower(AbstractCreature owner, int amount) {
        this.name = powerStrings.NAME;
        this.ID = "Study";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("draw");
        this.type = PowerType.BUFF;
        this.isTurnBased = false;
    }

    public void atEndOfTurn(boolean playerTurn) {
        addToBot((AbstractGameAction) new MakeTempCardInDrawPileAction((AbstractCard) new Insight(), this.amount, true,
                true));
    }

    public void updateDescription() {
        if (this.amount > 1) {
            this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
        } else {
            this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[2];
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * StudyPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


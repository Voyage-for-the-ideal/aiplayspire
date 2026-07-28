package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class LikeWaterPower extends AbstractPower {
    public static final String POWER_ID = "LikeWaterPower";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("LikeWaterPower");

    public LikeWaterPower(AbstractCreature owner, int amt) {
        this.name = powerStrings.NAME;
        this.ID = "LikeWaterPower";
        this.owner = owner;
        this.amount = amt;
        updateDescription();
        loadRegion("like_water");
    }

    public void stackPower(int stackAmount) {
        this.fontScale = 8.0F;
        this.amount += stackAmount;
        if (this.amount > 999) {
            this.amount = 999;
        }
        updateDescription();
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
    }

    public void atEndOfTurnPreEndTurnCards(boolean isPlayer) {
        if (isPlayer) {
            AbstractPlayer p = (AbstractPlayer) this.owner;
            if (p.stance.ID.equals("Calm")) {
                flash();
                addToBot((AbstractGameAction) new GainBlockAction(this.owner, this.owner, this.amount));
            }
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * LikeWaterPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


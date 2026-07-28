package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.EmptyDeckShuffleAction;
import com.megacrit.cardcrawl.actions.utility.ScryAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class ForesightPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("WireheadingPower");
    public static final String POWER_ID = "WireheadingPower";

    public ForesightPower(AbstractCreature owner, int scryAmt) {
        this.name = powerStrings.NAME;
        this.ID = "WireheadingPower";
        this.owner = owner;
        this.amount = scryAmt;
        updateDescription();
        loadRegion("wireheading");
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
    }

    public void atStartOfTurn() {
        if (AbstractDungeon.player.drawPile.size() <= 0) {

            addToTop((AbstractGameAction) new EmptyDeckShuffleAction());
        }
        flash();
        addToBot((AbstractGameAction) new ScryAction(this.amount));
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * ForesightPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


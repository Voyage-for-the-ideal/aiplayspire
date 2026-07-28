package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.watcher.ChangeStanceAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class DevotionPower extends AbstractPower {
    public static final String POWER_ID = "DevotionPower";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("DevotionPower");

    public DevotionPower(AbstractCreature owner, int newAmount) {
        this.name = powerStrings.NAME;
        this.ID = "DevotionPower";
        this.owner = owner;
        this.amount = newAmount;
        updateDescription();
        loadRegion("devotion");
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
    }

    public void atStartOfTurnPostDraw() {
        flash();
        if (!AbstractDungeon.player.hasPower("Mantra") && this.amount >= 10) {
            addToBot((AbstractGameAction) new ChangeStanceAction("Divinity"));
        } else {
            addToBot((AbstractGameAction) new ApplyPowerAction(this.owner, this.owner,
                    new MantraPower(this.owner, this.amount), this.amount));
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * DevotionPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


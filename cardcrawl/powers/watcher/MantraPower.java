package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.actions.watcher.ChangeStanceAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.powers.AbstractPower;

public class MantraPower extends AbstractPower {
    public static final String POWER_ID = "Mantra";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Mantra");
    private final int PRAYER_REQUIRED = 10;

    public MantraPower(AbstractCreature owner, int amount) {
        this.name = powerStrings.NAME;
        this.ID = "Mantra";
        this.owner = owner;
        this.amount = amount;
        updateDescription();
        loadRegion("mantra");
        this.type = PowerType.BUFF;
        AbstractDungeon.actionManager.mantraGained += amount;
    }

    public void playApplyPowerSfx() {
        CardCrawlGame.sound.play("POWER_MANTRA", 0.05F);
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0] + '\n' + powerStrings.DESCRIPTIONS[1];
    }

    public void stackPower(int stackAmount) {
        super.stackPower(stackAmount);
        if (this.amount >= 10) {
            addToTop((AbstractGameAction) new ChangeStanceAction("Divinity"));
            this.amount -= 10;
            if (this.amount <= 0)
                addToTop((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "Mantra"));
        }
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * MantraPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


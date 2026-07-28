package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.vfx.cardManip.ExhaustCardEffect;

public class TimeMazePower extends AbstractPower {
    public static final String POWER_ID = "TimeMazePower";
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("TimeMazePower");
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESC = powerStrings.DESCRIPTIONS;
    private int maxAmount;

    public TimeMazePower(AbstractCreature owner, int maxAmount) {
        this.name = NAME;
        this.ID = "TimeMazePower";
        this.owner = owner;
        this.amount = maxAmount;
        this.maxAmount = maxAmount;
        updateDescription();
        loadRegion("time");
        this.type = PowerType.BUFF;
    }

    public void updateDescription() {
        this.description = DESC[0] + this.maxAmount + DESC[1];
    }

    public void onAfterUseCard(AbstractCard card, UseCardAction action) {
        flashWithoutSound();
        this.amount--;
        if (this.amount == 0) {
            this.amount = this.maxAmount;
            AbstractDungeon.actionManager.cardQueue.clear();
            for (AbstractCard c : AbstractDungeon.player.limbo.group) {
                AbstractDungeon.effectList.add(new ExhaustCardEffect(c));
            }
            AbstractDungeon.player.limbo.group.clear();
            AbstractDungeon.player.releaseCard();
            AbstractDungeon.overlayMenu.endTurnButton.disable(true);
        }
        updateDescription();
    }

    public void atStartOfTurn() {
        this.amount = 15;
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * TimeMazePower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


package com.megacrit.cardcrawl.powers;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;

public class SporeCloudPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Spore Cloud");
    public static final String POWER_ID = "Spore Cloud";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESCRIPTIONS = powerStrings.DESCRIPTIONS;

    public SporeCloudPower(AbstractCreature owner, int vulnAmt) {
        this.name = NAME;
        this.ID = "Spore Cloud";
        this.owner = owner;
        this.amount = vulnAmt;
        updateDescription();
        loadRegion("sporeCloud");
    }

    public void updateDescription() {
        this.description = DESCRIPTIONS[0] + this.amount + DESCRIPTIONS[1];
    }

    public void onDeath() {
        if (AbstractDungeon.getCurrRoom().isBattleEnding()) {
            return;
        }
        CardCrawlGame.sound.play("SPORE_CLOUD_RELEASE");
        flashWithoutSound();
        addToTop((AbstractGameAction) new ApplyPowerAction((AbstractCreature) AbstractDungeon.player, null,
                new VulnerablePower((AbstractCreature) AbstractDungeon.player, this.amount, true), this.amount));
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * SporeCloudPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


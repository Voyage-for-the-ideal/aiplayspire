package com.megacrit.cardcrawl.powers;

import com.badlogic.gdx.graphics.Color;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.vfx.BorderFlashEffect;
import com.megacrit.cardcrawl.vfx.combat.TimeWarpTurnEndEffect;

public class TimeWarpPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("Time Warp");
    public static final String POWER_ID = "Time Warp";
    public static final String NAME = powerStrings.NAME;
    public static final String[] DESC = powerStrings.DESCRIPTIONS;
    private static final int STR_AMT = 2;
    private static final int COUNTDOWN_AMT = 12;

    public TimeWarpPower(AbstractCreature owner) {
        this.name = NAME;
        this.ID = "Time Warp";
        this.owner = owner;
        this.amount = 0;
        updateDescription();
        loadRegion("time");
        this.type = PowerType.BUFF;
    }

    public void playApplyPowerSfx() {
        CardCrawlGame.sound.play("POWER_TIME_WARP", 0.05F);
    }

    public void updateDescription() {
        this.description = DESC[0] + '\f' + DESC[1] + '\002' + DESC[2];
    }

    public void onAfterUseCard(AbstractCard card, UseCardAction action) {
        flashWithoutSound();
        this.amount++;
        if (this.amount == 12) {
            this.amount = 0;
            playApplyPowerSfx();
            AbstractDungeon.actionManager.callEndTurnEarlySequence();

            CardCrawlGame.sound.play("POWER_TIME_WARP", 0.05F);
            AbstractDungeon.effectsQueue.add(new BorderFlashEffect(Color.GOLD, true));
            AbstractDungeon.topLevelEffectsQueue.add(new TimeWarpTurnEndEffect());

            for (AbstractMonster m : (AbstractDungeon.getMonsters()).monsters) {
                addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) m, (AbstractCreature) m,
                        new StrengthPower((AbstractCreature) m, 2), 2));
            }
        }
        updateDescription();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\
 * TimeWarpPower.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


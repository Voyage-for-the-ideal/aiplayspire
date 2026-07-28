package com.megacrit.cardcrawl.powers.watcher;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.actions.common.RemoveSpecificPowerAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.PowerStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.WeakPower;

public class WaveOfTheHandPower extends AbstractPower {
    private static final PowerStrings powerStrings = CardCrawlGame.languagePack.getPowerStrings("WaveOfTheHandPower");
    public static final String POWER_ID = "WaveOfTheHandPower";

    public WaveOfTheHandPower(AbstractCreature owner, int newAmount) {
        this.name = powerStrings.NAME;
        this.ID = "WaveOfTheHandPower";
        this.owner = owner;
        this.amount = newAmount;
        updateDescription();
        loadRegion("wave_of_the_hand");
    }

    public void onGainedBlock(float blockAmount) {
        if (blockAmount > 0.0F) {
            flash();
            AbstractPlayer abstractPlayer = AbstractDungeon.player;
            for (AbstractMonster mo : (AbstractDungeon.getCurrRoom()).monsters.monsters) {
                addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) mo,
                        (AbstractCreature) abstractPlayer,
                        (AbstractPower) new WeakPower((AbstractCreature) mo, this.amount, false), this.amount, true,
                        AbstractGameAction.AttackEffect.NONE));
            }
        }
    }

    public void atEndOfRound() {
        addToBot((AbstractGameAction) new RemoveSpecificPowerAction(this.owner, this.owner, "WaveOfTheHandPower"));
    }

    public void updateDescription() {
        this.description = powerStrings.DESCRIPTIONS[0] + this.amount + powerStrings.DESCRIPTIONS[1];
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\powers\watcher\
 * WaveOfTheHandPower.class Java compiler version: 8 (52.0) JD-Core Version:
 * 1.1.3
 */


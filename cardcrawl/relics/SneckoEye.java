package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.ConfusionPower;

public class SneckoEye extends AbstractRelic {
    public static final String ID = "Snecko Eye";

    public SneckoEye() {
        super("Snecko Eye", "sneckoEye.png", RelicTier.BOSS, LandingSound.FLAT);
    }
    public static final int HAND_MODIFICATION = 2;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public void onEquip() {
        AbstractDungeon.player.masterHandSize += 2;
    }

    public void onUnequip() {
        AbstractDungeon.player.masterHandSize -= 2;
    }

    public void atPreBattle() {
        flash();
        addToBot((AbstractGameAction) new ApplyPowerAction((AbstractCreature) AbstractDungeon.player,
                (AbstractCreature) AbstractDungeon.player,
                (AbstractPower) new ConfusionPower((AbstractCreature) AbstractDungeon.player)));
    }

    public AbstractRelic makeCopy() {
        return new SneckoEye();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\SneckoEye.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


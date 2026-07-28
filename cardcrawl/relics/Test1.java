package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;

public class Test1 extends AbstractRelic {
    public static final String ID = "Test 1";

    public Test1() {
        super("Test 1", "test1.png", RelicTier.UNCOMMON, LandingSound.FLAT);
    }
    private static final int ENERGY_AMT = 1;

    public String getUpdatedDescription() {
        if (AbstractDungeon.player != null) {
            return setDescription(AbstractDungeon.player.chosenClass);
        }
        return setDescription((AbstractPlayer.PlayerClass) null);
    }

    private String setDescription(AbstractPlayer.PlayerClass c) {
        return this.DESCRIPTIONS[0] + this.DESCRIPTIONS[1];
    }

    public void updateDescription(AbstractPlayer.PlayerClass c) {
        this.description = setDescription(c);
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        initializeTips();
    }

    public void onUsePotion() {
        flash();
        addToBot((AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
        addToBot((AbstractGameAction) new GainEnergyAction(1));
    }

    public AbstractRelic makeCopy() {
        return new Test1();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Test1.class
 * Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


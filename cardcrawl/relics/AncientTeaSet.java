package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainEnergyAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;

public class AncientTeaSet extends AbstractRelic {
    public static final String ID = "Ancient Tea Set";

    public AncientTeaSet() {
        super("Ancient Tea Set", "tea_set.png", RelicTier.COMMON, LandingSound.SOLID);
    }
    private static final int ENERGY_AMT = 2;
    private boolean firstTurn = true;

    public String getUpdatedDescription() {
        if (AbstractDungeon.player != null) {
            return setDescription(AbstractDungeon.player.chosenClass);
        }
        return setDescription((AbstractPlayer.PlayerClass) null);
    }

    private String setDescription(AbstractPlayer.PlayerClass c) {
        return this.DESCRIPTIONS[0];
    }

    public void updateDescription(AbstractPlayer.PlayerClass c) {
        this.description = setDescription(c);
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        initializeTips();
    }

    public void atTurnStart() {
        if (this.firstTurn) {
            if (this.counter == -2) {
                this.pulse = false;
                this.counter = -1;
                flash();
                addToTop((AbstractGameAction) new GainEnergyAction(2));
                addToTop((AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player,
                        this));
            }
            this.firstTurn = false;
        }
    }

    public void atPreBattle() {
        this.firstTurn = true;
    }

    public void setCounter(int counter) {
        super.setCounter(counter);
        if (counter == -2) {
            this.pulse = true;
        }
    }

    public void onEnterRestRoom() {
        flash();
        this.counter = -2;
        this.pulse = true;
    }

    public boolean canSpawn() {
        return (Settings.isEndless || AbstractDungeon.floorNum <= 48);
    }

    public AbstractRelic makeCopy() {
        return new AncientTeaSet();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * AncientTeaSet.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


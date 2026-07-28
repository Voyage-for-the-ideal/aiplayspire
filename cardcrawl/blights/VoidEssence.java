package com.megacrit.cardcrawl.blights;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.BlightStrings;

public class VoidEssence extends AbstractBlight {
    public static final String ID = "VoidEssence";
    private static final BlightStrings blightStrings = CardCrawlGame.languagePack.getBlightString("VoidEssence");
    public static final String NAME = blightStrings.NAME;
    public static final String[] DESC = blightStrings.DESCRIPTION;

    public VoidEssence() {
        super("VoidEssence", NAME, DESC[0] + "1" + DESC[1], "void.png", false);
        this.counter = 1;
        updateDescription();
    }

    public void stack() {
        this.counter++;
        updateDescription();
        if (AbstractDungeon.player.energy.energyMaster > 0) {
            AbstractDungeon.player.energy.energyMaster--;
        }
        flash();
    }

    public void updateDescription(AbstractPlayer.PlayerClass c) {
        this.description = DESC[0] + this.counter + DESC[1];

        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        initializeTips();
    }

    public void updateDescription() {
        if (AbstractDungeon.player != null) {
            this.description = DESC[0] + this.counter + DESC[1];
        }

        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        initializeTips();
    }

    public void onEquip() {
        if (AbstractDungeon.player.energy.energyMaster > 0)
            AbstractDungeon.player.energy.energyMaster--;
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\blights\
 * VoidEssence.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




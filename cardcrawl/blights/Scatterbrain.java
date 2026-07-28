package com.megacrit.cardcrawl.blights;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.BlightStrings;

public class Scatterbrain extends AbstractBlight {
    public static final String ID = "Scatterbrain";
    private static final BlightStrings blightStrings = CardCrawlGame.languagePack.getBlightString("Scatterbrain");
    public static final String NAME = blightStrings.NAME;
    public static final String[] DESC = blightStrings.DESCRIPTION;

    public Scatterbrain() {
        super("Scatterbrain", NAME, DESC[0] + '\001' + DESC[1], "scatter.png", false);
        this.counter = 1;
    }

    public void stack() {
        AbstractDungeon.player.masterHandSize--;
        this.counter++;
        updateDescription();
        flash();
    }

    public void updateDescription() {
        this.description = DESC[0] + this.counter + DESC[1];
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        initializeTips();
    }

    public void onEquip() {
        AbstractDungeon.player.masterHandSize--;
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\blights\
 * Scatterbrain.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




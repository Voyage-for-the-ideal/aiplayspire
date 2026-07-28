package com.megacrit.cardcrawl.blights;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.BlightStrings;

public class Shield extends AbstractBlight {
    public static final String ID = "ToughEnemies";
    private static final BlightStrings blightStrings = CardCrawlGame.languagePack.getBlightString("ToughEnemies");
    public static final String NAME = blightStrings.NAME;
    public static final String[] DESC = blightStrings.DESCRIPTION;
    public float toughMod = 1.5F;

    public Shield() {
        super("ToughEnemies", NAME, DESC[0] + '2' + DESC[1], "shield.png", true);
        this.counter = 1;
    }

    public void incrementUp() {
        this.toughMod += 0.5F;
        this.increment++;
        this.counter++;
        this.description = DESC[0] + (int) ((this.toughMod - 1.0F) * 100.0F) + DESC[1];
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        initializeTips();
    }

    public float effectFloat() {
        return this.toughMod;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\blights\Shield.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




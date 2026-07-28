package com.megacrit.cardcrawl.blights;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.helpers.PowerTip;
import com.megacrit.cardcrawl.localization.BlightStrings;

public class Spear extends AbstractBlight {
    public static final String ID = "DeadlyEnemies";
    private static final BlightStrings blightStrings = CardCrawlGame.languagePack.getBlightString("DeadlyEnemies");
    public static final String NAME = blightStrings.NAME;
    public static final String[] DESC = blightStrings.DESCRIPTION;
    public float damageMod = 2.0F;

    public Spear() {
        super("DeadlyEnemies", NAME, DESC[0] + 'd' + DESC[1], "spear.png", true);
        this.counter = 1;
    }

    public void incrementUp() {
        this.damageMod += 0.75F;
        this.increment++;
        this.counter++;
        this.description = DESC[0] + (int) ((this.damageMod - 1.0F) * 100.0F) + DESC[1];
        this.tips.clear();
        this.tips.add(new PowerTip(this.name, this.description));
        initializeTips();
    }

    public float effectFloat() {
        return this.damageMod;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\blights\Spear.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




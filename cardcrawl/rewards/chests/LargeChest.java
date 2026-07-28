package com.megacrit.cardcrawl.rewards.chests;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class LargeChest
        extends AbstractChest {
    public LargeChest() {
        this.img = ImageMaster.L_CHEST;
        this.openedImg = ImageMaster.L_CHEST_OPEN;

        this.hb = new Hitbox(340.0F * Settings.scale, 200.0F * Settings.scale);
        this.hb.move(CHEST_LOC_X, CHEST_LOC_Y - 120.0F * Settings.scale);

        this.COMMON_CHANCE = 0;
        this.UNCOMMON_CHANCE = 75;
        this.RARE_CHANCE = 25;
        this.GOLD_CHANCE = 50;
        this.GOLD_AMT = 75;

        randomizeReward();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\rewards\chests\
 * LargeChest.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


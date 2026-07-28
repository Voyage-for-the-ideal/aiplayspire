package com.megacrit.cardcrawl.rewards.chests;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;

public class MediumChest
        extends AbstractChest {
    public MediumChest() {
        this.img = ImageMaster.M_CHEST;
        this.openedImg = ImageMaster.M_CHEST_OPEN;

        this.hb = new Hitbox(256.0F * Settings.scale, 270.0F * Settings.scale);
        this.hb.move(CHEST_LOC_X, CHEST_LOC_Y - 90.0F * Settings.scale);

        this.COMMON_CHANCE = 35;
        this.UNCOMMON_CHANCE = 50;
        this.RARE_CHANCE = 15;
        this.GOLD_CHANCE = 35;
        this.GOLD_AMT = 50;

        randomizeReward();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\rewards\chests\
 * MediumChest.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


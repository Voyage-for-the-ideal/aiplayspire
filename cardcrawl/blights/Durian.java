package com.megacrit.cardcrawl.blights;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.BlightStrings;

public class Durian extends AbstractBlight {
    public static final String ID = "BlightedDurian";
    private static final BlightStrings blightStrings = CardCrawlGame.languagePack.getBlightString("BlightedDurian");
    public static final String NAME = blightStrings.NAME;
    public static final String[] DESC = blightStrings.DESCRIPTION;

    public Durian() {
        super("BlightedDurian", NAME, DESC[0] + '2' + DESC[1], "durian.png", false);
        this.counter = 1;
    }

    public void stack() {
        this.counter++;
        AbstractDungeon.player.decreaseMaxHealth(AbstractDungeon.player.maxHealth / 2);
    }

    public void onEquip() {
        AbstractDungeon.player.decreaseMaxHealth(AbstractDungeon.player.maxHealth / 2);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\blights\Durian.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




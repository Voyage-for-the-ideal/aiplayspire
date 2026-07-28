package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Brewmaster extends AbstractDailyMod {
    public static final String ID = "Brewmaster";
    private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Brewmaster");
    public static final String NAME = modStrings.NAME, DESC = modStrings.DESCRIPTION;

    public Brewmaster() {
        super("Brewmaster", NAME, DESC, "brewmaster.png", true);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\daily\mods\
 * Brewmaster.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


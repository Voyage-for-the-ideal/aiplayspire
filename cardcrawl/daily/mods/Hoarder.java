package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Hoarder extends AbstractDailyMod {
    public static final String ID = "Hoarder";
    private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Hoarder");
    public static final String NAME = modStrings.NAME, DESC = modStrings.DESCRIPTION;

    public Hoarder() {
        super("Hoarder", NAME, DESC, "greed.png", false);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\daily\mods\Hoarder
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


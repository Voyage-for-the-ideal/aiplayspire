package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Endless extends AbstractDailyMod {
    public static final String ID = "Endless";
    private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Endless");
    public static final String NAME = modStrings.NAME, DESC = modStrings.DESCRIPTION;

    public Endless() {
        super("Endless", NAME, DESC, "endless.png", true);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\daily\mods\Endless
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


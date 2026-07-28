package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class Allstar extends AbstractDailyMod {
    public static final String ID = "Allstar";
    private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Allstar");
    public static final String NAME = modStrings.NAME, DESC = modStrings.DESCRIPTION;

    public Allstar() {
        super("Allstar", NAME, DESC, "all_star.png", true);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\daily\mods\Allstar
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


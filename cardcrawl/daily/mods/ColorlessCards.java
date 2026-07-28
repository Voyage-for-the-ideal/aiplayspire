package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class ColorlessCards extends AbstractDailyMod {
    public static final String ID = "Colorless Cards";
    private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Colorless Cards");
    public static final String NAME = modStrings.NAME, DESC = modStrings.DESCRIPTION;

    public ColorlessCards() {
        super("Colorless Cards", NAME, DESC, "colorless.png", true);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\daily\mods\
 * ColorlessCards.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class DeadlyEvents extends AbstractDailyMod {
    public static final String ID = "DeadlyEvents";
    private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("DeadlyEvents");
    public static final String NAME = modStrings.NAME, DESC = modStrings.DESCRIPTION;

    public DeadlyEvents() {
        super("DeadlyEvents", NAME, DESC, "deadly_events.png", false);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\daily\mods\
 * DeadlyEvents.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


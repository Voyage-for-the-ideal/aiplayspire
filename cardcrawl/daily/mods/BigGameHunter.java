package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class BigGameHunter extends AbstractDailyMod {
    public static final String ID = "Elite Swarm";
    private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Elite Swarm");
    public static final String NAME = modStrings.NAME, DESC = modStrings.DESCRIPTION;

    public BigGameHunter() {
        super("Elite Swarm", NAME, DESC, "elite_swarm.png", false);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\daily\mods\
 * BigGameHunter.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


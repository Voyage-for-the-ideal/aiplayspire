package com.megacrit.cardcrawl.daily.mods;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.localization.RunModStrings;

public class GreenCards extends AbstractDailyMod {
    public static final String ID = "Green Cards";
    private static final RunModStrings modStrings = CardCrawlGame.languagePack.getRunModString("Green Cards");
    public static final String NAME = modStrings.NAME, DESC = modStrings.DESCRIPTION;

    public GreenCards() {
        super("Green Cards", NAME, DESC, "green.png", true, AbstractPlayer.PlayerClass.THE_SILENT);
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\daily\mods\
 * GreenCards.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


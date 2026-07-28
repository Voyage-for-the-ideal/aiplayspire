package com.megacrit.cardcrawl.trials;

import java.util.ArrayList;

public class DraftTrial
        extends AbstractTrial {
    public boolean keepsStarterCards() {
        return false;
    }

    public ArrayList<String> dailyModIDs() {
        ArrayList<String> retVal = new ArrayList<>();
        retVal.add("Draft");
        return retVal;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\trials\DraftTrial.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


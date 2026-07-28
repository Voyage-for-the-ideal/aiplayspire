package com.megacrit.cardcrawl.trials;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StarterDeckTrial
        extends AbstractTrial {
    public List<String> extraStartingRelicIDs() {
        return Collections.singletonList("Busted Crown");
    }

    public ArrayList<String> dailyModIDs() {
        ArrayList<String> retVal = new ArrayList<>();
        retVal.add("Binary");
        return retVal;
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\trials\
 * StarterDeckTrial.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


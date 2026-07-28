package com.megacrit.cardcrawl.trials;

import com.megacrit.cardcrawl.characters.AbstractPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestTrial
        extends AbstractTrial {
    public AbstractPlayer setupPlayer(AbstractPlayer player) {
        player.maxHealth = 20;
        player.currentHealth = 10;
        player.gold = 777;
        return player;
    }

    public boolean keepStarterRelic() {
        return false;
    }

    public List<String> extraStartingRelicIDs() {
        return Arrays.asList(new String[] {"Derp Rock", "Unceasing Top" });
    }

    public boolean keepsStarterCards() {
        return true;
    }

    public List<String> extraStartingCardIDs() {
        return Arrays.asList(new String[] {"Demon Form", "Wraith Form v2", "Echo Form" });
    }

    public boolean useRandomDailyMods() {
        return false;
    }

    public ArrayList<String> dailyModIDs() {
        ArrayList<String> retVal = new ArrayList<>();
        retVal.add("Diverse");
        retVal.add("Lethality");
        retVal.add("Time Dilation");
        retVal.add("Cursed Run");
        retVal.add("Elite Swarm");
        return retVal;
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\trials\TestTrial.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


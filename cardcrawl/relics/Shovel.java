package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.ui.campfire.AbstractCampfireOption;
import com.megacrit.cardcrawl.ui.campfire.DigOption;

import java.util.ArrayList;

public class Shovel
        extends AbstractRelic {
    public static final String ID = "Shovel";

    public Shovel() {
        super("Shovel", "shovel.png", RelicTier.RARE, LandingSound.FLAT);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public boolean canSpawn() {
        if (AbstractDungeon.floorNum >= 48 && !Settings.isEndless) {
            return false;
        }

        int campfireRelicCount = 0;

        for (AbstractRelic r : AbstractDungeon.player.relics) {
            if (r instanceof PeacePipe || r instanceof Shovel || r instanceof Girya) {
                campfireRelicCount++;
            }
        }

        return (campfireRelicCount < 2);
    }

    public void addCampfireOption(ArrayList<AbstractCampfireOption> options) {
        options.add(new DigOption());
    }

    public AbstractRelic makeCopy() {
        return new Shovel();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\Shovel.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


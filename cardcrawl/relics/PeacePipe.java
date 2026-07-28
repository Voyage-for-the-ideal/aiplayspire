package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.ui.campfire.AbstractCampfireOption;
import com.megacrit.cardcrawl.ui.campfire.TokeOption;

import java.util.ArrayList;

public class PeacePipe
        extends AbstractRelic {
    public static final String ID = "Peace Pipe";

    public PeacePipe() {
        super("Peace Pipe", "peacePipe.png", RelicTier.RARE, LandingSound.FLAT);
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
        options.add(new TokeOption(

                !CardGroup.getGroupWithoutBottledCards(AbstractDungeon.player.masterDeck.getPurgeableCards())
                        .isEmpty()));
    }

    public AbstractRelic makeCopy() {
        return new PeacePipe();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\PeacePipe.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


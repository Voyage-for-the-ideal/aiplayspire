package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class MealTicket extends AbstractRelic {
    public static final String ID = "MealTicket";

    public MealTicket() {
        super("MealTicket", "mealTicket.png", RelicTier.COMMON, LandingSound.CLINK);
    }
    private static final int HP_AMT = 15;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\017' + this.DESCRIPTIONS[1];
    }

    public void justEnteredRoom(AbstractRoom room) {
        if (room instanceof com.megacrit.cardcrawl.rooms.ShopRoom) {
            flash();
            addToTop(
                    (AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
            AbstractDungeon.player.heal(15);
        }
    }

    public boolean canSpawn() {
        return (Settings.isEndless || AbstractDungeon.floorNum <= 48);
    }

    public AbstractRelic makeCopy() {
        return new MealTicket();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\MealTicket.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


package com.megacrit.cardcrawl.relics;

import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class MagicFlower
        extends AbstractRelic {
    public static final String ID = "Magic Flower";
    private static final float HEAL_MULTIPLIER = 1.5F;

    public MagicFlower() {
        super("Magic Flower", "magicFlower.png", RelicTier.RARE, LandingSound.MAGICAL);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }

    public int onPlayerHeal(int healAmount) {
        if (AbstractDungeon.currMapNode != null
                && (AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT) {
            flash();
            return MathUtils.round(healAmount * 1.5F);
        }
        return healAmount;
    }

    public AbstractRelic makeCopy() {
        return new MagicFlower();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\MagicFlower
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


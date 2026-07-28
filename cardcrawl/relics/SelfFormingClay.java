package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.ApplyPowerAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.powers.NextTurnBlockPower;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

public class SelfFormingClay extends AbstractRelic {
    public static final String ID = "Self Forming Clay";

    public SelfFormingClay() {
        super("Self Forming Clay", "clay.png", RelicTier.UNCOMMON, LandingSound.FLAT);
    }
    private static final int BLOCK_AMT = 3;

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\003' + this.DESCRIPTIONS[1];
    }

    public void wasHPLost(int damageAmount) {
        if ((AbstractDungeon.getCurrRoom()).phase == AbstractRoom.RoomPhase.COMBAT &&
                damageAmount > 0) {
            flash();
            addToTop((AbstractGameAction) new ApplyPowerAction((AbstractCreature) AbstractDungeon.player,
                    (AbstractCreature) AbstractDungeon.player,
                    (AbstractPower) new NextTurnBlockPower((AbstractCreature) AbstractDungeon.player, 3, this.name),
                    3));
        }
    }

    public AbstractRelic makeCopy() {
        return new SelfFormingClay();
    }
}

/*
 * Location: E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\
 * SelfFormingClay.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


package com.megacrit.cardcrawl.actions.defect;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.orbs.AbstractOrb;

public class ChannelAction
        extends AbstractGameAction {
    private AbstractOrb orbType;
    private boolean autoEvoke = false;

    public ChannelAction(AbstractOrb newOrbType) {
        this(newOrbType, true);
    }

    public ChannelAction(AbstractOrb newOrbType, boolean autoEvoke) {
        this.duration = Settings.ACTION_DUR_FAST;
        this.orbType = newOrbType;
        this.autoEvoke = autoEvoke;
    }

    public void update() {
        if (this.duration == Settings.ACTION_DUR_FAST) {
            if (this.autoEvoke) {
                AbstractDungeon.player.channelOrb(this.orbType);
            } else {

                for (AbstractOrb o : AbstractDungeon.player.orbs) {
                    if (o instanceof com.megacrit.cardcrawl.orbs.EmptyOrbSlot) {
                        AbstractDungeon.player.channelOrb(this.orbType);

                        break;
                    }
                }
            }
            if (Settings.FAST_MODE) {
                this.isDone = true;

                return;
            }
        }
        tickDuration();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\actions\defect\
 * ChannelAction.class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */




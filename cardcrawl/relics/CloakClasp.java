package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.common.GainBlockAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class CloakClasp extends AbstractRelic {
    public CloakClasp() {
        super("CloakClasp", "clasp.png", RelicTier.RARE, LandingSound.CLINK);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0] + '\001' + this.DESCRIPTIONS[1];
    }
    public static final String ID = "CloakClasp";

    public void onPlayerEndTurn() {
        if (!AbstractDungeon.player.hand.group.isEmpty()) {
            flash();
            addToBot((AbstractGameAction) new GainBlockAction((AbstractCreature) AbstractDungeon.player, null,
                    AbstractDungeon.player.hand.group.size() * 1));
        }
    }

    public AbstractRelic makeCopy() {
        return new CloakClasp();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\CloakClasp.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */


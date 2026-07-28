package com.megacrit.cardcrawl.relics;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.animations.TalkAction;
import com.megacrit.cardcrawl.actions.common.RelicAboveCreatureAction;
import com.megacrit.cardcrawl.actions.utility.SFXAction;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

public class CultistMask extends AbstractRelic {
    public CultistMask() {
        super("CultistMask", "cultistMask.png", RelicTier.SPECIAL, LandingSound.CLINK);
    }

    public String getUpdatedDescription() {
        return this.DESCRIPTIONS[0];
    }
    public static final String ID = "CultistMask";

    public void atBattleStart() {
        flash();
        addToBot((AbstractGameAction) new RelicAboveCreatureAction((AbstractCreature) AbstractDungeon.player, this));
        addToBot((AbstractGameAction) new SFXAction("VO_CULTIST_1A"));
        addToBot((AbstractGameAction) new TalkAction(true, this.DESCRIPTIONS[1], 1.0F, 2.0F));
    }

    public AbstractRelic makeCopy() {
        return new CultistMask();
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\relics\CultistMask
 * .class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

